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

class ModelValidator(private val strict: Boolean = false) {

    fun validateModel(model: ModelDefinition): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationError>()

        // Validate class-level annotations
        validateClassAnnotations(model, errors, warnings)

        // Validate properties
        model.properties.forEach { property ->
            validateProperty(property, errors, warnings)
        }

        // Validate constructor if it's a data class
        validateConstructor(model, errors)

        // Additional strict validations
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

    private fun validateClassAnnotations(
        model: ModelDefinition,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationError>
    ) {
        // Check for @Serializable annotation
        if (!model.annotations.containsKey("Serializable")) {
            errors += ValidationError.error(
                field = null,
                code = "CLASS_NOT_SERIALIZABLE",
                message = "Class ${model.name} must be annotated with @Serializable"
            )
        }

        // Check if it's a data class (recommended)
        if (!model.content.contains("data class")) {
            warnings += ValidationError.warning(
                field = null,
                code = "NOT_DATA_CLASS",
                message = "Class ${model.name} is not a data class. Consider using 'data class' for better serialization."
            )
        }

        // Check for proper visibility
        if (model.content.contains("private class") || model.content.contains("internal class")) {
            warnings += ValidationError.warning(
                field = null,
                code = "CLASS_NOT_SERIALIZABLE",
                message = "Class ${model.name} has restricted visibility. This may affect serialization."
            )
        }
    }

    private fun validateProperty(
        property: PropertyInfo,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationError>
    ) {
        // Check property name conventions
        if (property.name.contains(Regex("[A-Z]"))) {
            warnings += ValidationError.warning(
                field = property.name,
                code = "PROPERTY_NAMING",
                message = "Property ${property.name} contains uppercase letters. Consider using camelCase."
            )
        }

        // Validate annotations
        property.annotations.forEach { (annotationName, params) ->
            when (annotationName) {
                "Field" -> validateFieldAnnotation(property, params, errors)
                "Email" -> validateEmailAnnotation(property, errors)
                "Url" -> validateUrlAnnotation(property, errors)
                "Pattern" -> validatePatternAnnotation(property, params, errors)
                "Range" -> validateRangeAnnotation(property, params, errors)
                // Add more annotation validations as needed
            }
        }

        // Check type compatibility with annotations
        validateTypeCompatibility(property, errors, warnings)

        // Check nullable vs required
        validateNullableRequired(property, warnings)
    }

    private fun validateFieldAnnotation(
        property: PropertyInfo,
        params: Map<String, Any>,
        errors: MutableList<ValidationError>
    ) {
        // Validate min/max for numeric types
        if (property.type in listOf("Int", "Long", "Double", "Float")) {
            params["min"]?.let { min ->
                if (min !is Number) {
                    errors.add(
                        ValidationError.error(
                            field = property.name,
                            code = "MIN",
                            message = "Property ${property.name}: min must be a number",
                            value = property.type
                        )
                    )
                }
            }

            params["max"]?.let { max ->
                if (max !is Number) {
                    errors += ValidationError.error(
                        field = property.name,
                        code = "INVALID_TYPE",
                        message = "Property ${property.name}: max must be a number"
                    )
                }

                // Check if max > min
                params["min"]?.let { min ->
                    if (max is Number && min is Number && max.toDouble() <= min.toDouble()) {
                        errors += ValidationError.error(
                            field = property.name,
                            code = "INVALID_TYPE",
                            message = "Property ${property.name}: max must be greater than min"
                        )
                    }
                }
            }
        }

        // Validate minLength/maxLength for strings
        if (property.type == "String") {
            params["minLength"]?.let { minLen ->
                if (minLen !is Int || minLen < 0) {
                    errors += ValidationError.error(
                        field = property.name,
                        code = "INVALID_TYPE",
                        message = "Property ${property.name}: minLength must be a non-negative integer"
                    )
                }
            }

            params["maxLength"]?.let { maxLen ->
                if (maxLen !is Int || maxLen < 0) {
                    errors += ValidationError.error(
                        field = property.name,
                        code = "INVALID_TYPE",
                        message = "Property ${property.name}: maxLength must be a non-negative integer"
                    )
                }

                // Check if maxLength > minLength
                params["minLength"]?.let { minLen ->
                    if (maxLen is Int && minLen is Int && maxLen <= minLen) {
                        errors += ValidationError.error(
                            field = property.name,
                            code = "INVALID_RANGE",
                            message = "Property ${property.name}: maxLength must be greater than minLength"
                        )
                    }
                }
            }
        }

        // Validate pattern
        params["pattern"]?.let { pattern ->
            if (pattern is String) {
                try {
                    Regex(pattern)
                } catch (_: Exception) {
                    errors += ValidationError.error(
                        field = property.name,
                        code = "INVALID_PATTERN",
                        message = "Property ${property.name}: Invalid regex pattern: $pattern"
                    )
                }
            }
        }

        // Validate default value
        params["defaultValue"]?.let { defaultValue ->
            if (defaultValue is String && defaultValue.isNotEmpty()) {
                validateDefaultValue(property, defaultValue, errors)
            }
        }
    }

    private fun validateEmailAnnotation(
        property: PropertyInfo,
        errors: MutableList<ValidationError>
    ) {
        if (property.type != "String") {
            errors += ValidationError.error(
                field = property.name,
                code = "INVALID_TYPE",
                message = "Property ${property.name}: @Email can only be applied to String properties"
            )
        }
    }

    private fun validateUrlAnnotation(
        property: PropertyInfo,
        errors: MutableList<ValidationError>
    ) {
        if (property.type != "String") {
            errors += ValidationError.error(
                field = property.name,
                code = "INVALID_TYPE",
                message = "Property ${property.name}: @Url can only be applied to String properties"
            )
        }
    }

    private fun validatePatternAnnotation(
        property: PropertyInfo,
        params: Map<String, Any>,
        errors: MutableList<ValidationError>
    ) {
        if (property.type != "String") {
            errors += ValidationError.error(
                field = property.name,
                code = "INVALID_PATTERN",
                message = "Property ${property.name}: @Pattern can only be applied to String properties"
            )
        }

        params["regex"]?.let { regex ->
            if (regex is String) {
                try {
                    Regex(regex)
                } catch (_: Exception) {
                    errors += ValidationError.error(
                        field = property.name,
                        code = "INVALID_PATTERN",
                        message = "Property ${property.name}: Invalid regex pattern in @Pattern: $regex"
                    )
                }
            }
        }
    }

    private fun validateRangeAnnotation(
        property: PropertyInfo,
        params: Map<String, Any>,
        errors: MutableList<ValidationError>
    ) {
        if (property.type !in listOf("Int", "Long", "Double", "Float")) {
            errors += ValidationError.error(
                field = property.name,
                code = "INVALID_TYPE",
                message = "Property ${property.name}: @Range can only be applied to numeric properties"
            )
        }

        val min = params["min"] as? Long
        val max = params["max"] as? Long

        if (min == null || max == null) {
            errors += ValidationError.error(
                field = property.name,
                code = "INVALID_TYPE",
                message = "Property ${property.name}: @Range requires both min and max parameters"
            )
        } else if (max <= min) {
            errors += ValidationError.error(
                field = property.name,
                code = "INVALID_TYPE",
                message = "Property ${property.name}: @Range max must be greater than min"
            )
        }
    }

    private fun validateTypeCompatibility(
        property: PropertyInfo,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationError>
    ) {
        // Check if annotation matches property type
        property.annotations.forEach { (annotationName, _) ->
            when (annotationName) {
                "Min", "Max", "Range" -> {
                    if (property.type !in listOf("Int", "Long", "Double", "Float")) {
                        errors += ValidationError.error(
                            field = property.name,
                            code = "INVALID_TYPE",
                            message = "Property ${property.name}: @$annotationName can only be applied to numeric properties"
                        )
                    }
                }

                "Size", "Pattern" -> {
                    if (property.type != "String" && !property.type.startsWith("List<") && !property.type.startsWith("Array<")) {
                        warnings += ValidationError.warning(
                            field = property.name,
                            code = "INVALID_TYPE",
                            message = "Property ${property.name}: @$annotationName is typically used with String or collection types"
                        )
                    }
                }
            }
        }
    }

    private fun validateNullableRequired(
        property: PropertyInfo,
        warnings: MutableList<ValidationError>
    ) {
        val isNullable = property.isNullable
        val isRequired = property.annotations["Field"]?.get("required") as? Boolean ?: true

        if (!isNullable && !isRequired) {
            warnings += ValidationError.warning(
                field = property.name,
                code = "NON_NULLABLE",
                message = "Property ${property.name}: Non-nullable property marked as not required"
            )
        }
    }

    private fun validateDefaultValue(
        property: PropertyInfo,
        defaultValue: String,
        errors: MutableList<ValidationError>
    ) {
        try {
            when (property.type) {
                "Int" -> defaultValue.toInt()
                "Long" -> defaultValue.toLong()
                "Double" -> defaultValue.toDouble()
                "Float" -> defaultValue.toFloat()
                "Boolean" -> defaultValue.toBoolean()
                // String doesn't need conversion
            }
        } catch (_: Exception) {
            errors += ValidationError.error(
                field = property.name,
                code = "INVALID_DEFAULT_VALUE",
                message = "Property ${property.name}: Invalid default value '$defaultValue' for type ${property.type}"
            )
        }
    }

    private fun validateConstructor(
        model: ModelDefinition,
        warnings: MutableList<ValidationError>
    ) {
        // Check if constructor parameters match properties
        val constructorRegex = """\s*(?:public\s+)?constructor\s*\((.*?)\)""".toRegex()
        val constructorMatch = constructorRegex.find(model.content)

        if (constructorMatch != null) {
            val params = constructorMatch.groupValues[1]
            val paramNames = params.split(',').map { it.trim().split(':').first() }

            val propertyNames = model.properties.map { it.name }

            if (paramNames != propertyNames) {
                warnings += ValidationError.warning(
                    field = model.name,
                    code = "CONSTRUCTOR_PARAMETERS_MISMATCH",
                    message = "Constructor parameters don't match property names. This may cause serialization issues."
                )
            }
        }
    }

    private fun validateStrictRules(
        model: ModelDefinition,
        warnings: MutableList<ValidationError>
    ) {
        // All properties must have validation annotations in strict mode
        model.properties.forEach { property ->
            if (property.annotations.isEmpty()) {
                warnings += ValidationError.warning(
                    field = property.name,
                    code = "NOT_EMPTY",
                    message = "Property ${property.name} has no validation annotations in strict mode",
                )
            }
        }

        // Check for consistent naming
        val hasSnakeCase = model.properties.any { it.name.contains('_') }
        val hasCamelCase = model.properties.any { it.name.matches(Regex("^[a-z]+[A-Z][a-zA-Z]*$")) }

        if (hasSnakeCase && hasCamelCase) {
            warnings += ValidationError.warning(
                field = model.name,
                code = "MIXED_NAMING_CONVENTION",
                message = "Mixed naming conventions (snake_case and camelCase) found in ${model.name}"
            )
        }

        // Check for documentation
        if (!model.content.contains("/**") && !model.content.contains("/*")) {
            warnings += ValidationError.warning(
                field = model.name,
                code = "LACK_DOCS",
                message = "Class ${model.name} lacks documentation. Consider adding Javadoc/KDoc."
            )
        }
    }
}
