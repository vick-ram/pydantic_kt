package com.pydantic.runtime.validation

data class ModelInfo(
    val name: String,
    val packageName: String,
    val properties: List<PropertyInfo>,
    val annotations: Map<String, String>,
    val isStrict: Boolean
)

data class PropertyInfo(
    val name: String,
    val type: String,
    val annotations: Map<String, Map<String, Any>>,
    val isNullable: Boolean
)

data class ModelDefinition(
    val name: String,
    val content: String,
    val sourceFile: String,
    val properties: List<PropertyInfo> = emptyList(),
    val annotations: Map<String, String> = emptyMap()
)

data class ValidationResult(
    val modelName: String,
    val isValid: Boolean,
    val errors: List<ValidationError>,
    val warnings: List<ValidationError>
)

/**
 * Validates model definitions with support for both annotation-based
 * and delegation-based validation approaches.
 */
class ModelValidator(private val strict: Boolean = false) {

    /**
     * Main validation entry point.
     */
    fun validateModel(model: ModelDefinition): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationError>()

        validateClassStructure(model, errors, warnings)
        validateProperties(model, errors, warnings)
        validateConstructor(model, warnings)

        if (strict) {
            validateStrictRules(model, warnings)
        }

        return ValidationResult(
            modelName = model.name,
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    /**
     * Validates the overall class structure including both annotations and delegates.
     */
    private fun validateClassStructure(
        model: ModelDefinition,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationError>
    ) {
        // Check for @Serializable annotation OR delegation pattern
        val hasSerializationSupport = model.annotations.containsKey("Serializable") ||
                containsDelegationPattern(model.content)

        if (!hasSerializationSupport) {
            errors.add(ValidationError.error(
                field = null,
                code = "CLASS_NOT_SERIALIZABLE",
                message = "Class ${model.name} must be annotated with @Serializable or use delegation pattern"
            ))
        }

        // Check if it's a data class (recommended)
        if (!model.content.contains("data class")) {
            warnings.add(ValidationError.warning(
                field = null,
                code = "NOT_DATA_CLASS",
                message = "Class ${model.name} is not a data class. Consider using 'data class' for better serialization."
            ))
        }

        // Check visibility
        if (model.content.contains("private class") || model.content.contains("internal class")) {
            warnings.add(ValidationError.warning(
                field = null,
                code = "RESTRICTED_VISIBILITY",
                message = "Class ${model.name} has restricted visibility. This may affect serialization."
            ))
        }
    }

    /**
     * Validates all properties using both annotation and delegation approaches.
     */
    private fun validateProperties(
        model: ModelDefinition,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationError>
    ) {
        // First, detect delegation patterns from model content
        val propertyDelegationInfo = extractDelegationInfoFromModel(model)

        model.properties.forEach { property ->
            val delegationInfo = propertyDelegationInfo[property.name]
            validateProperty(property, delegationInfo, errors, warnings)
        }
    }

    /**
     * Validates individual property with delegation info.
     */
    private fun validateProperty(
        property: PropertyInfo,
        delegationInfo: DelegationInfo?,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationError>
    ) {
        // Check property naming
        validatePropertyNaming(property, warnings)

        // Determine validation approach
        val validationApproach = detectValidationApproach(property, delegationInfo)

        when (validationApproach) {
            ValidationApproach.ANNOTATIONS -> validateAnnotationBasedProperty(property, errors, warnings)
            ValidationApproach.DELEGATION -> validateDelegationBasedProperty(property, delegationInfo!!, errors, warnings)
            ValidationApproach.MIXED -> validateMixedApproachProperty(property, delegationInfo, errors, warnings)
            ValidationApproach.NONE -> validateNoValidationProperty(property, warnings)
        }
    }

    /**
     * Detects which validation approach is being used for the property.
     */
    private fun detectValidationApproach(
        property: PropertyInfo,
        delegationInfo: DelegationInfo?
    ): ValidationApproach {
        val hasAnnotations = property.annotations.isNotEmpty()
        val hasDelegation = delegationInfo != null

        return when {
            hasAnnotations && hasDelegation -> ValidationApproach.MIXED
            hasAnnotations -> ValidationApproach.ANNOTATIONS
            hasDelegation -> ValidationApproach.DELEGATION
            else -> ValidationApproach.NONE
        }
    }

    /**
     * Extracts delegation information from the model content.
     */
    private fun extractDelegationInfoFromModel(model: ModelDefinition): Map<String, DelegationInfo> {
        val delegationInfo = mutableMapOf<String, DelegationInfo>()

        // Pattern to match property declarations with delegation
        val propertyPattern = Regex("""val\s+(\w+)\s*:\s*\w+\s+by\s+(\w+)\s*\{([^}]*)\}""")
        val allMatches = propertyPattern.findAll(model.content)

        allMatches.forEach { match ->
            val propertyName = match.groupValues[1]
            val delegateType = match.groupValues[2]
            val lambdaContent = match.groupValues[3]

            val params = parseDelegateLambda(lambdaContent)

            delegationInfo[propertyName] = DelegationInfo(
                delegateType = delegateType,
                parameters = params,
                lambdaContent = lambdaContent
            )
        }

        return delegationInfo
    }

    /**
     * Parses delegate lambda content into parameters.
     */
    private fun parseDelegateLambda(lambdaContent: String): Map<String, Any> {
        val params = mutableMapOf<String, Any>()

        // Extract parameter assignments like min = 5, max = 10
        val paramRegex = Regex("""(\w+)\s*=\s*([^,\n]+)""")
        paramRegex.findAll(lambdaContent).forEach { match ->
            val key = match.groupValues[1].trim()
            val valueStr = match.groupValues[2].trim()

            val value = parseValue(valueStr)
            if (value != null) {
                params[key] = value
            }
        }

        // Also check for marker methods like .email()
        val markerRegex = Regex("""\.(\w+)\s*\(\s*\)""")
        markerRegex.findAll(lambdaContent).forEach { match ->
            val markerName = match.groupValues[1]
            params["__marker_$markerName"] = true
        }

        return params
    }

    /**
     * Parses a string value into appropriate type.
     */
    private fun parseValue(valueStr: String): Any? {
        return when {
            valueStr.startsWith("\"") && valueStr.endsWith("\"") ->
                valueStr.removeSurrounding("\"")
            valueStr.toIntOrNull() != null ->
                valueStr.toInt()
            valueStr.toLongOrNull() != null ->
                valueStr.toLong()
            valueStr.toDoubleOrNull() != null ->
                valueStr.toDouble()
            valueStr.toFloatOrNull() != null ->
                valueStr.toFloat()
            valueStr == "true" || valueStr == "false" ->
                valueStr.toBoolean()
            else -> valueStr
        }
    }

    /**
     * Checks if model content contains delegation patterns.
     */
    private fun containsDelegationPattern(content: String): Boolean {
        return Regex("""by\s+\w+\s*\{""").containsMatchIn(content)
    }

    // ============================================
    // Annotation-based validation methods
    // ============================================

    private fun validateAnnotationBasedProperty(
        property: PropertyInfo,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationError>
    ) {
        // Validate annotations
        property.annotations.forEach { (annotationName, params) ->
            when (annotationName) {
                "Field" -> validateFieldAnnotation(property, params, errors)
                "Email" -> validateEmailAnnotation(property, errors)
                "Url" -> validateUrlAnnotation(property, errors)
                "Pattern" -> validatePatternAnnotation(property, params, errors)
                "Range" -> validateRangeAnnotation(property, params, errors)
                "Size" -> validateSizeAnnotation(property, params, errors)
                "Min", "Max" -> validateMinMaxAnnotation(property, annotationName, params, errors)
            }
        }

        // Check type compatibility
        validateTypeCompatibility(property, errors, warnings)
    }

    // ============================================
    // Delegation-based validation methods
    // ============================================

    private fun validateDelegationBasedProperty(
        property: PropertyInfo,
        delegationInfo: DelegationInfo,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationError>
    ) {
        val delegateType = delegationInfo.delegateType
        val params = delegationInfo.parameters

        // Validate delegate type matches property type
        validateDelegateTypeCompatibility(property, delegateType, errors)

        // Validate delegate parameters
        validateDelegateParameters(property, delegateType, params, errors, warnings)

        // Check for marker annotations in delegates
        validateDelegateMarkers(property, delegateType, params, errors)
    }

    private fun validateMixedApproachProperty(
        property: PropertyInfo,
        delegationInfo: DelegationInfo?,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationError>
    ) {
        warnings.add(ValidationError.warning(
            field = property.name,
            code = "MIXED_VALIDATION_APPROACH",
            message = "Property ${property.name}: Mixing annotations and delegation may cause conflicts"
        ))

        // Validate annotation approach
        validateAnnotationBasedProperty(property, errors, warnings)

        // Validate delegation approach if present
        if (delegationInfo != null) {
            validateDelegationBasedProperty(property, delegationInfo, errors, warnings)
        }
    }

    private fun validateNoValidationProperty(
        property: PropertyInfo,
        warnings: MutableList<ValidationError>
    ) {
        if (strict) {
            warnings.add(ValidationError.warning(
                field = property.name,
                code = "NO_VALIDATION",
                message = "Property ${property.name}: No validation defined"
            ))
        }
    }

    /**
     * Validates that delegate type is compatible with property type.
     */
    private fun validateDelegateTypeCompatibility(
        property: PropertyInfo,
        delegateType: String,
        errors: MutableList<ValidationError>
    ) {
        val normalizedDelegate = normalizeDelegateType(delegateType)
        val normalizedProperty = normalizePropertyType(property.type)

        val typeMapping = mapOf(
            "string" to listOf("string", "charsequence"),
            "int" to listOf("int", "integer", "number"),
            "long" to listOf("long", "number"),
            "float" to listOf("float", "number"),
            "double" to listOf("double", "number"),
            "boolean" to listOf("boolean", "bool"),
            "date" to listOf("date", "localdate"),
            "datetime" to listOf("datetime", "localdatetime", "instant", "date"),
            "time" to listOf("time", "localtime")
        )

        val allowedTypes = typeMapping[normalizedDelegate] ?: listOf("any")

        if (normalizedProperty !in allowedTypes && "any" !in allowedTypes) {
            errors.add(ValidationError.error(
                field = property.name,
                code = "TYPE_MISMATCH",
                message = "Property ${property.name}: Type ${property.type} cannot use $delegateType delegate. " +
                        "Expected: ${allowedTypes.joinToString(", ")}"
            ))
        }
    }

    /**
     * Validates parameters for a specific delegate type.
     */
    private fun validateDelegateParameters(
        property: PropertyInfo,
        delegateType: String,
        params: Map<String, Any>,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationError>
    ) {
        val normalizedType = normalizeDelegateType(delegateType)
        val rules = DELEGATE_RULES[normalizedType]

        if (rules != null) {
            // Check for unknown parameters
            params.keys.forEach { param ->
                if (param.startsWith("__marker_")) {
                    // Skip marker parameters, they're handled separately
                    return@forEach
                }

                val normalizedParam = PARAM_ALIASES[param] ?: param
                if (normalizedParam !in rules.fieldParams) {
                    warnings.add(ValidationError.warning(
                        field = property.name,
                        code = "UNKNOWN_DELEGATE_PARAM",
                        message = "Property ${property.name}: Unknown parameter '$param' for $delegateType delegate"
                    ))
                }
            }

            // Type-specific validations
            when (normalizedType) {
                "string" -> validateStringDelegateParameters(property, params, errors)
                "int", "long", "float", "double" ->
                    validateNumericDelegateParameters(property, normalizedType, params, errors, warnings)
                "date", "time", "datetime" ->
                    validateTemporalDelegateParameters(property, normalizedType, params, errors, warnings)
            }
        } else {
            warnings.add(ValidationError.warning(
                field = property.name,
                code = "UNKNOWN_DELEGATE_TYPE",
                message = "Property ${property.name}: Unknown delegate type '$delegateType'"
            ))
        }
    }

    /**
     * Validates string delegate parameters.
     */
    private fun validateStringDelegateParameters(
        property: PropertyInfo,
        params: Map<String, Any>,
        errors: MutableList<ValidationError>
    ) {
        // Validate length constraints
        params["minLength"]?.let { minLength ->
            if (minLength is Int && minLength < 0) {
                errors.add(ValidationError.error(
                    field = property.name,
                    code = "INVALID_MIN_LENGTH",
                    message = "Property ${property.name}: minLength must be non-negative"
                ))
            }
        }

        params["maxLength"]?.let { maxLength ->
            if (maxLength is Int && maxLength < 0) {
                errors.add(ValidationError.error(
                    field = property.name,
                    code = "INVALID_MAX_LENGTH",
                    message = "Property ${property.name}: maxLength must be non-negative"
                ))
            }
        }

        // Validate pattern if present
        params["pattern"]?.let { pattern ->
            if (pattern is String) {
                try {
                    Regex(pattern)
                } catch (e: Exception) {
                    errors.add(ValidationError.error(
                        field = property.name,
                        code = "INVALID_PATTERN",
                        message = "Property ${property.name}: Invalid regex pattern: $pattern"
                    ))
                }
            }
        }
    }

    /**
     * Validates numeric delegate parameters.
     */
    private fun validateNumericDelegateParameters(
        property: PropertyInfo,
        type: String,
        params: Map<String, Any>,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationError>
    ) {
        val min = params["min"] ?: params["greaterThan"]
        val max = params["max"] ?: params["lessThan"]

        if (min != null && max != null) {
            when (min) {
                is Number if max is Number && max.toDouble() <= min.toDouble() -> {
                    errors.add(ValidationError.error(
                        field = property.name,
                        code = "INVALID_RANGE",
                        message = "Property ${property.name}: max must be greater than min"
                    ))
                }

                max -> {
                    warnings.add(ValidationError.warning(
                        field = property.name,
                        code = "DEGENERATE_RANGE",
                        message = "Property ${property.name}: min and max are equal"
                    ))
                }
            }
        }

        // Validate divisibleBy for integers
        if (type == "int" || type == "long") {
            params["divisibleBy"]?.let { divisor ->
                if (divisor is Number && divisor.toDouble() == 0.0) {
                    errors.add(ValidationError.error(
                        field = property.name,
                        code = "DIVISION_BY_ZERO",
                        message = "Property ${property.name}: divisibleBy cannot be zero"
                    ))
                }
            }
        }
    }

    /**
     * Validates temporal delegate parameters.
     */
    private fun validateTemporalDelegateParameters(
        property: PropertyInfo,
        type: String,
        params: Map<String, Any>,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationError>
    ) {
        val before = params["before"]
        val after = params["after"]

        if (before != null && after != null) {
            warnings.add(ValidationError.warning(
                field = property.name,
                code = "TEMPORAL_CONSTRAINTS",
                message = "Property ${property.name}: Both before and after constraints specified"
            ))
        }

        // Validate date/time string formats if they're strings
        listOf(before, after).forEach { value ->
            if (value is String) {
                // Could add specific date format validation here
                if (!value.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
                    warnings.add(ValidationError.warning(
                        field = property.name,
                        code = "DATE_FORMAT",
                        message = "Property ${property.name}: Date value '$value' may not be in ISO format (YYYY-MM-DD)"
                    ))
                }
            }
        }
    }

    /**
     * Validates marker methods in delegates.
     */
    private fun validateDelegateMarkers(
        property: PropertyInfo,
        delegateType: String,
        params: Map<String, Any>,
        errors: MutableList<ValidationError>
    ) {
        val normalizedType = normalizeDelegateType(delegateType)
        val rules = DELEGATE_RULES[normalizedType]

        rules?.markerAnnotations?.forEach { (markerKey, annotationName) ->
            if (params.containsKey("__marker_$markerKey")) {
                // Validate marker compatibility
                when (markerKey) {
                    "email", "url", "uuid", "slug", "ascii", "trimmed",
                    "lowercase", "uppercase", "notBlank", "notEmpty" -> {
                        if (normalizedType != "string") {
                            errors.add(ValidationError.error(
                                field = property.name,
                                code = "INVALID_MARKER",
                                message = "Property ${property.name}: .$markerKey() can only be used with string delegates"
                            ))
                        }
                    }
                    "weekday", "weekend" -> {
                        if (normalizedType != "date" && normalizedType != "datetime") {
                            errors.add(ValidationError.error(
                                field = property.name,
                                code = "INVALID_MARKER",
                                message = "Property ${property.name}: .$markerKey() can only be used with date/time delegates"
                            ))
                        }
                    }
                }
            }
        }
    }

    // ============================================
    // Original annotation validation methods (simplified for brevity)
    // ============================================

    private fun validatePropertyNaming(
        property: PropertyInfo,
        warnings: MutableList<ValidationError>
    ) {
        if (property.name.contains(Regex("[A-Z]"))) {
            warnings.add(ValidationError.warning(
                field = property.name,
                code = "PROPERTY_NAMING",
                message = "Property ${property.name} contains uppercase letters. Consider using camelCase."
            ))
        }
    }

    private fun validateTypeCompatibility(
        property: PropertyInfo,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationError>
    ) {
        property.annotations.forEach { (annotationName, _) ->
            when (annotationName) {
                "Min", "Max", "Range" -> {
                    if (property.type !in NUMERIC_TYPES) {
                        errors.add(ValidationError.error(
                            field = property.name,
                            code = "INVALID_TYPE",
                            message = "Property ${property.name}: @$annotationName can only be applied to numeric properties"
                        ))
                    }
                }
                "Size", "Pattern" -> {
                    if (property.type != "String" &&
                        !property.type.startsWith("List<") &&
                        !property.type.startsWith("Array<")) {
                        warnings.add(ValidationError.warning(
                            field = property.name,
                            code = "INCOMPATIBLE_ANNOTATION",
                            message = "Property ${property.name}: @$annotationName is typically used with String or collection types"
                        ))
                    }
                }
            }
        }
    }

    private fun validateFieldAnnotation(
        property: PropertyInfo,
        params: Map<String, Any>,
        errors: MutableList<ValidationError>
    ) {
        if (property.type in NUMERIC_TYPES) {
            params["min"]?.let { if (it !is Number) {
                errors.add(ValidationError.error(
                    field = property.name,
                    code = "INVALID_MIN",
                    message = "Property ${property.name}: min must be a number"
                ))
            }}
            params["max"]?.let { if (it !is Number) {
                errors.add(ValidationError.error(
                    field = property.name,
                    code = "INVALID_MAX",
                    message = "Property ${property.name}: max must be a number"
                ))
            }}
        }

        if (property.type == "String") {
            params["minLength"]?.let { if (it !is Int || it < 0) {
                errors.add(ValidationError.error(
                    field = property.name,
                    code = "INVALID_MIN_LENGTH",
                    message = "Property ${property.name}: minLength must be non-negative integer"
                ))
            }}
        }
    }

    private fun validateEmailAnnotation(
        property: PropertyInfo,
        errors: MutableList<ValidationError>
    ) {
        if (property.type != "String") {
            errors.add(ValidationError.error(
                field = property.name,
                code = "INVALID_EMAIL_ANNOTATION",
                message = "Property ${property.name}: @Email can only be applied to String properties"
            ))
        }
    }

    // Add other annotation validation methods here...

    // ============================================
    // Constructor and strict mode validations
    // ============================================

    private fun validateConstructor(
        model: ModelDefinition,
        warnings: MutableList<ValidationError>
    ) {
        val constructorRegex = Regex("""constructor\s*\((.*?)\)""")
        val constructorMatch = constructorRegex.find(model.content)

        if (constructorMatch != null) {
            val params = constructorMatch.groupValues[1]
            val paramNames = params.split(',').map { it.trim().split(':').first() }
            val propertyNames = model.properties.map { it.name }

            if (paramNames != propertyNames) {
                warnings.add(ValidationError.warning(
                    field = model.name,
                    code = "CONSTRUCTOR_MISMATCH",
                    message = "Constructor parameters don't match property names"
                ))
            }
        }
    }

    private fun validateStrictRules(
        model: ModelDefinition,
        warnings: MutableList<ValidationError>
    ) {
        val hasSnakeCase = model.properties.any { it.name.contains('_') }
        val hasCamelCase = model.properties.any { it.name.matches(Regex("^[a-z]+[A-Z][a-zA-Z]*$")) }

        if (hasSnakeCase && hasCamelCase) {
            warnings.add(ValidationError.warning(
                field = model.name,
                code = "MIXED_NAMING",
                message = "Mixed naming conventions found in ${model.name}"
            ))
        }

        if (!model.content.contains("/**") && !model.content.contains("/*")) {
            warnings.add(ValidationError.warning(
                field = model.name,
                code = "NO_DOCUMENTATION",
                message = "Class ${model.name} lacks documentation"
            ))
        }
    }

    private fun validateUrlAnnotation(
        property: PropertyInfo,
        errors: MutableList<ValidationError>
    ) {
        if (property.type != "String") {
            errors.add(ValidationError.error(
                field = property.name,
                code = "INVALID_URL_ANNOTATION",
                message = "Property ${property.name}: @Url can only be applied to String properties"
            ))
        }
    }

    private fun validatePatternAnnotation(
        property: PropertyInfo,
        params: Map<String, Any>,
        errors: MutableList<ValidationError>
    ) {
        if (property.type != "String") {
            errors.add(ValidationError.error(
                field = property.name,
                code = "INVALID_PATTERN_ANNOTATION",
                message = "Property ${property.name}: @Pattern can only be applied to String properties"
            ))
        }

        params["regexp"]?.let { regexp ->
            if (regexp is String) {
                try {
                    Regex(regexp)
                } catch (e: Exception) {
                    errors.add(ValidationError.error(
                        field = property.name,
                        code = "INVALID_REGEX_PATTERN",
                        message = "Property ${property.name}: Invalid regex pattern in @Pattern: $regexp"
                    ))
                }
            }
        }

        params["flags"]?.let { flags ->
            val iterableFlags: Iterable<*>? = when (flags) {
                is Array<*> -> flags.asIterable()
                is List<*> -> flags
                else -> null
            }

            iterableFlags?.forEach { flag ->
                if (flag is String) {
                    val validFlags = listOf(
                        "CASE_INSENSITIVE", "MULTILINE", "DOTALL", "UNICODE_CASE",
                        "CANON_EQ", "UNIX_LINES", "LITERAL", "COMMENTS"
                    )
                    if (flag !in validFlags) {
                        errors.add(
                            ValidationError.error(
                                field = property.name,
                                code = "INVALID_PATTERN_FLAG",
                                message = "Property ${property.name}: Invalid flag '$flag' in @Pattern"
                            )
                        )
                    }
                }
            }
        }
    }

    private fun validateRangeAnnotation(
        property: PropertyInfo,
        params: Map<String, Any>,
        errors: MutableList<ValidationError>
    ) {
        if (property.type !in NUMERIC_TYPES) {
            errors.add(ValidationError.error(
                field = property.name,
                code = "INVALID_RANGE_ANNOTATION",
                message = "Property ${property.name}: @Range can only be applied to numeric properties"
            ))
        }

        val min = params["min"] as? Number
        val max = params["max"] as? Number

        when {
            min == null || max == null -> {
                errors.add(ValidationError.error(
                    field = property.name,
                    code = "MISSING_RANGE_PARAMETERS",
                    message = "Property ${property.name}: @Range requires both min and max parameters"
                ))
            }
            max.toDouble() <= min.toDouble() -> {
                errors.add(ValidationError.error(
                    field = property.name,
                    code = "INVALID_RANGE_VALUES",
                    message = "Property ${property.name}: @Range max must be greater than min"
                ))
            }
        }
    }

    private fun validateSizeAnnotation(
        property: PropertyInfo,
        params: Map<String, Any>,
        errors: MutableList<ValidationError>
    ) {
        // @Size can be applied to String, Collection, Array, Map
        val isValidType = property.type == "String" ||
                property.type.startsWith("List<") ||
                property.type.startsWith("Array<") ||
                property.type.startsWith("Set<") ||
                property.type.startsWith("Map<") ||
                property.type == "Collection<*>"

        if (!isValidType) {
            errors.add(ValidationError.error(
                field = property.name,
                code = "INVALID_SIZE_ANNOTATION",
                message = "Property ${property.name}: @Size can only be applied to String, Collection, Array or Map properties"
            ))
        }

        val min = params["min"] as? Int
        val max = params["max"] as? Int

        min?.let {
            if (it < 0) {
                errors.add(ValidationError.error(
                    field = property.name,
                    code = "INVALID_SIZE_MIN",
                    message = "Property ${property.name}: @Size min must be non-negative"
                ))
            }
        }

        max?.let {
            if (it < 0) {
                errors.add(ValidationError.error(
                    field = property.name,
                    code = "INVALID_SIZE_MAX",
                    message = "Property ${property.name}: @Size max must be non-negative"
                ))
            }
        }

        if (min != null && max != null && max < min) {
            errors.add(ValidationError.error(
                field = property.name,
                code = "INVALID_SIZE_RANGE",
                message = "Property ${property.name}: @Size max must be greater than or equal to min"
            ))
        }
    }

    private fun validateMinMaxAnnotation(
        property: PropertyInfo,
        annotationName: String,
        params: Map<String, Any>,
        errors: MutableList<ValidationError>
    ) {
        if (property.type !in NUMERIC_TYPES) {
            errors.add(ValidationError.error(
                field = property.name,
                code = "INVALID_MINMAX_ANNOTATION",
                message = "Property ${property.name}: @$annotationName can only be applied to numeric properties"
            ))
        }

        val value = params["value"] as? Number
        if (value == null) {
            errors.add(ValidationError.error(
                field = property.name,
                code = "MISSING_MINMAX_VALUE",
                message = "Property ${property.name}: @$annotationName requires a 'value' parameter"
            ))
        }

        // Additional validation for specific constraints
        when (annotationName) {
            "Min" -> {
                // Could add validation for minimum value constraints
                // e.g., if (value.toDouble() < 0) { ... }
            }
            "Max" -> {
                // Could add validation for maximum value constraints
            }
        }
    }

    private fun validatePatternFlags(
        property: PropertyInfo,
        flags: List<String>,
        errors: MutableList<ValidationError>
    ) {
        val validFlags = listOf("CASE_INSENSITIVE", "MULTILINE", "DOTALL", "UNICODE_CASE",
            "CANON_EQ", "UNIX_LINES", "LITERAL", "COMMENTS")

        flags.forEach { flag ->
            if (flag !in validFlags) {
                errors.add(ValidationError.error(
                    field = property.name,
                    code = "INVALID_PATTERN_FLAG",
                    message = "Property ${property.name}: Invalid flag '$flag' in @Pattern"
                ))
            }
        }
    }

    // ============================================
    // Helper methods
    // ============================================

    private fun normalizeDelegateType(type: String): String {
        return type.lowercase()
            .removeSuffix("field")
            .removeSuffix("delegate")
            .trim()
    }

    private fun normalizePropertyType(type: String): String {
        return type.lowercase()
            .removePrefix("kotlin.")
            .removePrefix("java.")
            .removeSuffix("?")
            .trim()
    }

    companion object {
        private val NUMERIC_TYPES = listOf("Int", "Long", "Double", "Float")
    }
}

// ============================================
// Supporting data classes and enums
// ============================================

/**
 * Information about a property's delegation.
 */
data class DelegationInfo(
    val delegateType: String,
    val parameters: Map<String, Any>,
    val lambdaContent: String = ""
)

/**
 * Enum representing different validation approaches.
 */
private enum class ValidationApproach {
    ANNOTATIONS,
    DELEGATION,
    MIXED,
    NONE
}

/**
 * Delegate rules configuration.
 */
private val DELEGATE_RULES: Map<String, DelegateRules> = mapOf(
    "string" to DelegateRules(
        fieldParams = setOf("minLength", "maxLength", "pattern", "startsWith",
            "endsWith", "contains", "equalsTo"),
        markerAnnotations = mapOf(
            "email" to "Email",
            "url" to "Url",
            "uuid" to "Uuid",
            "slug" to "Slug",
            "ascii" to "Ascii",
            "trimmed" to "Trimmed",
            "lowercase" to "Lowercase",
            "uppercase" to "Uppercase",
            "notBlank" to "NotBlank",
            "notEmpty" to "NotEmpty"
        )
    ),
    "int" to DelegateRules(
        fieldParams = setOf("min", "max", "greaterThan", "lessThan",
            "equalsTo", "divisibleBy"),
        markerAnnotations = emptyMap()
    ),
    "long" to DelegateRules(
        fieldParams = setOf("min", "max", "gt", "lt", "equalsTo"),
        markerAnnotations = emptyMap()
    ),
    "float" to DelegateRules(
        fieldParams = setOf("min", "max", "gt", "lt"),
        markerAnnotations = emptyMap()
    ),
    "double" to DelegateRules(
        fieldParams = setOf("min", "max", "gt", "lt"),
        markerAnnotations = emptyMap()
    ),
    "date" to DelegateRules(
        fieldParams = setOf("before", "after"),
        markerAnnotations = mapOf(
            "weekday" to "Weekday",
            "weekend" to "Weekend"
        )
    ),
    "time" to DelegateRules(
        fieldParams = setOf("before", "after"),
        markerAnnotations = emptyMap()
    ),
    "datetime" to DelegateRules(
        fieldParams = setOf("before", "after"),
        markerAnnotations = mapOf(
            "weekday" to "Weekday",
            "weekend" to "Weekend"
        )
    )
)

val PARAM_ALIASES = mapOf(
    "gt" to "greaterThan",
    "lt" to "lessThan"
)

/**
 * Rules for a specific delegate type.
 */
data class DelegateRules(
    val fieldParams: Set<String>,
    val markerAnnotations: Map<String, String>
)


