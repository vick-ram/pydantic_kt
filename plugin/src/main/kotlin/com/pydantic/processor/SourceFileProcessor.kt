package com.pydantic.processor

import com.pydantic.runtime.validation.ModelInfo
import com.pydantic.runtime.validation.PropertyInfo
import java.io.File

class SourceFileProcessor {

    fun processFile(file: File, detectDelegates: Boolean = false): List<ModelInfo> {
        val content = file.readText()
        val annotationModels = extractModelsWithAnnotations(content)
        val delegatesModels = extractModelsWithDelegates(content)
        return if (detectDelegates) {
            annotationModels + delegatesModels
        } else {
            annotationModels
        }
    }

    // Original method for annotation-based models
    private fun extractModelsWithAnnotations(content: String): List<ModelInfo> {
        val models = mutableListOf<ModelInfo>()
        val packageName = extractPackageName(content)

        val classRegex = """(?m)^\s*@Serializable(?:\s*\(([^)]*)\))?\s+(?:(?:data|open|abstract|sealed)\s+)?class\s+(\w+)""".toRegex()
        val classMatches = classRegex.findAll(content)

        classMatches.forEach { match ->
            val annotationParams = match.groupValues[1]
            val className = match.groupValues[2]
            val classContent = extractClassContent(content, className)

            if (classContent.isNotEmpty()) {
                val modelInfo = parseClassWithAnnotations(className, packageName, classContent, annotationParams)
                models.add(modelInfo)
            }
        }

        return models
    }

    private fun extractModelsWithDelegates(content: String): List<ModelInfo> {
        val models = mutableListOf<ModelInfo>()
        val packageName = extractPackageName(content)

        val classRegex = """(?:class|data\s+class|open\s+class|abstract\s+class)\s+(\w+)\s*[:{\{]""".toRegex()
        val classMatches = classRegex.findAll(content)

        classMatches.forEach { match ->
            val className = match.groupValues[1]
            val classContent = extractClassContent(content, className)

            if (classContent.isNotEmpty()) {
                if (hasDelegateProperties(classContent)) {
                    val modelInfo = parseClassWithDelegates(className, packageName, classContent)
                    models.add(modelInfo)
                }
            }
        }

        return models
    }

    // Check if class contains delegate properties
    private fun hasDelegateProperties(classContent: String): Boolean {
        val delegateRegexCheck by lazy {
            val typeNames = DELEGATE_RULES.keys.joinToString("|")
            """by\s+(?:Field(?:\.\w+)?|(?:$typeNames)Field|field)\b""".toRegex(RegexOption.IGNORE_CASE)
        }
        return delegateRegexCheck.containsMatchIn(classContent)
    }

    private fun parseClassWithDelegates(
        className: String,
        packageName: String,
        classContent: String
    ): ModelInfo {
        val properties = extractDelegateProperties(classContent)

        return ModelInfo(
            name = className,
            packageName = packageName,
            properties = properties,
            annotations = mapOf("usesDelegates" to "true"),
            isStrict = false
        )
    }

    private fun extractDelegateProperties(classContent: String): List<PropertyInfo> {
        val properties = mutableListOf<PropertyInfo>()

        // Pattern 1: var name: Type by Field.string(minLength = 2, maxLength = 100)
        val simpleDelegatePattern =
            """(?:val|var)\s+(\w+)\s*:\s*([^=\n]+?)\s+by\s+(Field\.\w+|[Ff]ield)\(([^)]*)\)""".toRegex()

        simpleDelegatePattern.findAll(classContent).forEach { match ->
            val name = match.groupValues[1]
            val type = match.groupValues[2].trim()
            val delegateCall = match.groupValues[3] // Field.string or Field
            val paramsText = match.groupValues[4]

            val annotations = parseDelegateParams(paramsText, delegateCall)

            properties.add(
                PropertyInfo(
                    name = name,
                    type = type,
                    annotations = annotations,
                    isNullable = type.endsWith('?')
                )
            )
        }

        // Pattern 2: var name: Type by stringField { minLength(2) maxLength(100) }
        val dslDelegatePattern = """(?:val|var)\s+(\w+)\s*:\s*([^=\n]+?)\s+by\s+(\w+Field)\s*\{([^}]*)\}""".toRegex()

        dslDelegatePattern.findAll(classContent).forEach { match ->
            val name = match.groupValues[1]
            val type = match.groupValues[2].trim()
            val delegateType = match.groupValues[3] // stringField, intField, etc.
            val dslContent = match.groupValues[4]

            val annotations = parseDslDelegateParams(dslContent, delegateType)

            properties.add(
                PropertyInfo(
                    name = name,
                    type = type,
                    annotations = annotations,
                    isNullable = type.endsWith('?')
                )
            )
        }

        // Pattern 3: var name: Type by field { ... } (generic field)
        val genericFieldPattern = """(?:val|var)\s+(\w+)\s*:\s*([^=\n]+?)\s+by\s+field\s*\{([^}]*)\}""".toRegex()

        genericFieldPattern.findAll(classContent).forEach { match ->
            val name = match.groupValues[1]
            val type = match.groupValues[2].trim()
            val dslContent = match.groupValues[3]

            val annotations = parseGenericFieldParams(dslContent, type)

            properties.add(
                PropertyInfo(
                    name = name,
                    type = type,
                    annotations = annotations,
                    isNullable = type.endsWith('?')
                )
            )
        }

        return properties
    }

    private fun parseDelegateParams(paramsText: String, delegateCall: String): Map<String, Map<String, Any>> {
        val annotations = mutableMapOf<String, Map<String, Any>>()
        val fieldParams = mutableMapOf<String, Any>()

        val typeKey = normalizeTypeKey(delegateCall)
        val rules = DELEGATE_RULES[typeKey] ?: return emptyMap()

        val paramRegex = """(\w+)\s*=\s*([^,]+(?:\s*\([^)]*\))?)""".toRegex()

        paramRegex.findAll(paramsText).forEach { match ->
            val rawKey = match.groupValues[1].trim()
            val valueStr = match.groupValues[2].trim()
            val value = parseDelegateValue(valueStr)

            val key = PARAM_ALIASES[rawKey] ?: rawKey

            when {
                rules.markerAnnotations.containsKey(rawKey) -> {
                    if (value == true) {
                        val annotationName = rules.markerAnnotations[rawKey]!!
                        annotations[annotationName] = emptyMap()
                    }
                }

                rules.fieldParams.contains(key) -> {
                    fieldParams[key] = value
                }

                typeKey == "field" -> {
                    fieldParams[key] = value
                }
            }
        }

        if (fieldParams.isNotEmpty()) {
            annotations["Field"] = fieldParams
        }

        return annotations
    }

    private fun parseDslDelegateParams(dslContent: String, delegateType: String): Map<String, Map<String, Any>> {
        val annotations = mutableMapOf<String, Map<String, Any>>()
        val fieldParams = mutableMapOf<String, Any>()

        val typeKey = normalizeTypeKey(delegateType)
        val rules = DELEGATE_RULES[typeKey] ?: return emptyMap()

        val callRegex = Regex("""(\w+)\s*\(([^)]*)\)""")

        callRegex.findAll(dslContent).forEach { match ->
            val fn = match.groupValues[1]
            val rawArgs = match.groupValues[2]
            val normalizedFn = PARAM_ALIASES[fn] ?: fn

            rules.markerAnnotations[fn]?.let { annotationName ->
                annotations[annotationName] = emptyMap()
                return@forEach
            }

            val parsedArgs = rawArgs.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { parseDelegateValue(it) }

            when (normalizedFn) {
                "range", "between" -> {
                    fieldParams["min"]?.let { fieldParams["min"] = it }
                    fieldParams["max"]?.let { fieldParams["max"] = it }
                }

                "past", "future" -> {
                    fieldParams["amount"]?.let { fieldParams["amount"] = it }
                    fieldParams["unit"]?.let { fieldParams["unit"] = it }
                }

                else -> {
                    if (rules.fieldParams.contains(normalizedFn)) {
                        fieldParams[normalizedFn] = parsedArgs.firstOrNull() ?: true
                    }
                }
            }
        }
        if (fieldParams.isNotEmpty()) annotations["Field"] = fieldParams
        return annotations
    }

    private fun parseGenericFieldParams(dslContent: String, propertyType: String): Map<String, Map<String, Any>> {
        val annotations = mutableMapOf<String, Map<String, Any>>()
        val fieldParams = mutableMapOf<String, Any>()

        // Look for validator calls
        val validatorRegex = """validator\s*\{[^}]*\}""".toRegex()
        if (validatorRegex.containsMatchIn(dslContent)) {
            annotations["Validate"] = mapOf("custom" to "true")
        }

        // Look for initial value
        val initialRegex = """initial\(([^)]*)\)""".toRegex()
        initialRegex.find(dslContent)?.let { match ->
            val defaultValue = parseDelegateValue(match.groupValues[1])
            fieldParams["defaultValue"] = defaultValue
        }

        if (fieldParams.isNotEmpty()) {
            annotations["Field"] = fieldParams
        }

        return annotations
    }

    private fun parseDelegateValue(valueStr: String): Any {
        val trimmed = valueStr.trim()

        return when {
            // Booleans
            trimmed == "true" -> true
            trimmed == "false" -> false

            // Strings (remove quotes)
            trimmed.startsWith('"') && trimmed.endsWith('"') ->
                trimmed.substring(1, trimmed.length - 1)

            // Hex or Numbers
            trimmed.startsWith("0x") -> trimmed.removePrefix("0x").toLong(16)
            trimmed.matches(Regex("""^-?\d+$""")) -> trimmed.toLong()
            trimmed.matches(Regex("""^-?\d+\.\d+$""")) -> trimmed.toDouble()

            // Composite value objects (like rangeOf(1, 10))
            trimmed.contains("(") -> {
                val fnName = trimmed.substringBefore("(")
                val innerArgs = trimmed.substringAfter("(").substringBeforeLast(")")
                    .split(",").map { parseDelegateValue(it.trim()) }

                when (fnName) {
                    "rangeOf" -> mapOf("min" to innerArgs[0], "max" to innerArgs[1])
                    else -> trimmed // Fallback for unknown functions
                }
            }

            else -> trimmed
        }
    }

    // Keep all the original annotation parsing methods...
    private fun extractPackageName(content: String): String {
        val packageRegex = """(?m)^package\s+([\w.]+)""".toRegex()
        val match = packageRegex.find(content)

        return match?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun extractClassContent(content: String, className: String): String {
        // Look for "class Name", but handle potential annotations or keywords before it
        val start = content.indexOf("class $className")
        if (start == -1) return ""

        var braceCount = 0
        var parenCount = 0
        var hasStarted = false
        var end = content.length

        for (i in start until content.length) {
            val char = content[i]
            when (char) {
                '(' -> {
                    parenCount++
                    hasStarted = true
                }

                ')' -> {
                    parenCount--
                    if (parenCount == 0 && braceCount == 0 && hasStarted) {
                        // Check if a brace starts immediately after (ignoring whitespace)
                        val remaining = content.substring(i + 1).trimStart()
                        if (!remaining.startsWith("{")) {
                            end = i + 1
                            break
                        }
                    }
                }

                '{' -> {
                    braceCount++
                    hasStarted = true
                }

                '}' -> {
                    braceCount--
                    if (braceCount == 0 && parenCount == 0 && hasStarted) {
                        end = i + 1
                        break
                    }
                }
            }
        }

        // Fallback: If no delimiters were found, capture until the next class or end of file
        return content.substring(start, end)
    }

    private fun parseClassWithAnnotations(
        className: String,
        packageName: String,
        classContent: String,
        annotationParams: String
    ): ModelInfo {
        val annotations = mutableMapOf<String, String>()
        var isStrict = false

        if (annotationParams.isNotBlank()) {
            annotationParams.split(',').forEach { param ->
                val parts = param.split('=')
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim().removeSurrounding("\"")
                    annotations[key] = value
                    if (key == "strict" && value == "true") isStrict = true
                }
            }
        }

        val properties = extractPropertiesFromAnnotations(classContent)

        return ModelInfo(
            name = className,
            packageName = packageName,
            properties = properties,
            annotations = annotations,
            isStrict = isStrict
        )
    }

    private fun extractPropertiesFromAnnotations(classContent: String): List<PropertyInfo> {
        val properties = mutableListOf<PropertyInfo>()

        // Find the constructor start
        val constructorStartRegex = """(?:class|data class)\s+\w+\s*\(""".toRegex()
        val startMatch = constructorStartRegex.find(classContent) ?: return properties

        val startIndex = startMatch.range.last + 1 // Position after '('

        // Now find the matching closing parenthesis
        var parenDepth = 0
        var endIndex = startIndex

        for (i in startIndex until classContent.length) {
            when (classContent[i]) {
                '(' -> parenDepth++
                ')' -> {
                    if (parenDepth == 0) {
                        endIndex = i
                        break
                    }
                    parenDepth--
                }
            }
        }

        if (endIndex > startIndex) {
            val params = classContent.substring(startIndex, endIndex)
            val paramList = splitParams(params)
            paramList.forEach { param ->
                val property = parseProperty(param.trim())
                if (property != null) {
                    properties.add(property)
                }
            }
        }

        return properties
    }

    // Keep all other original methods...
    private fun splitParams(params: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var parenDepth = 0
        var angleDepth = 0

        for (char in params) {
            when (char) {
                '(' -> parenDepth++
                ')' -> parenDepth--
                '<' -> angleDepth++
                '>' -> angleDepth--
                ',' -> {
                    if (parenDepth == 0 && angleDepth == 0) {
                        result.add(current.toString())
                        current = StringBuilder()
                        continue
                    }
                }
            }
            current.append(char)
        }

        if (current.isNotEmpty()) {
            result.add(current.toString())
        }

        return result
    }

    private fun parseProperty(param: String): PropertyInfo? {
        // Removed ^ anchor and added (?s) to handle internal newlines if any
        // Added \s* to handle leading whitespace in the parameter string
        val pattern = """(?s)\s*(.*?)\s+(val|var)\s+(\w+)\s*:\s*([^=\n]+)(?:\s*=\s*(.*))?""".toRegex()
        val match = pattern.find(param) ?: return null

        val annotationsText = match.groupValues[1].trim()
        val name = match.groupValues[3]
        val type = match.groupValues[4].trim()
        // Group 5 might contain trailing commas or whitespace from splitParams
        val defaultValue = match.groupValues[5].trim().takeIf { it.isNotEmpty() }

        val annotations = parseAnnotations(annotationsText)

        return PropertyInfo(
            name = name,
            type = type,
            annotations = annotations,
            isNullable = type.endsWith('?'),
        )
    }

    private fun parseAnnotations(annotationsText: String): Map<String, Map<String, Any>> {
        val annotations = mutableMapOf<String, Map<String, Any>>()

        // Match annotations like @Field(min = 1, max = 100)
        val annotationRegex = """@(\w+)(?:\(([^)]*)\))?""".toRegex()
        val matches = annotationRegex.findAll(annotationsText)

        matches.forEach { match ->
            val annotationName = match.groupValues[1]
            val paramsText = match.groupValues[2]

            val params = mutableMapOf<String, Any>()
            if (paramsText.isNotEmpty()) {
                paramsText.split(',').forEach { param ->
                    val keyValue = param.trim().split('=')
                    if (keyValue.size == 2) {
                        val key = keyValue[0].trim()
                        val value = parseAnnotationValue(keyValue[1].trim())
                        params[key] = value
                    }
                }
            }

            annotations[annotationName] = params
        }

        return annotations
    }

    private fun parseAnnotationValue(value: String): Any {
        return when {
            value == "true" -> true
            value == "false" -> false
            value.startsWith('"') && value.endsWith('"') ->
                value.substring(1, value.length - 1)

            value.matches(Regex("""^\d+$""")) -> value.toLong()
            value.matches(Regex("""^\d+\.\d+$""")) -> value.toDouble()
            else -> value
        }
    }
}

private data class DelegateRules(
    val fieldParams: Set<String>,
    val markerAnnotations: Map<String, String>
)

private val DELEGATE_RULES: Map<String, DelegateRules> = mapOf(
    "string" to DelegateRules(
        fieldParams = setOf(
            "minLength",
            "maxLength",
            "pattern",
            "startsWith",
            "endsWith",
            "contains",
            "equalsTo"
        ),
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
        fieldParams = setOf("min", "max", "greaterThan", "lessThan", "equalsTo", "divisibleBy"),
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

    // Date / time delegates
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

private val PARAM_ALIASES = mapOf("gt" to "greaterThan", "lt" to "lessThan")

private fun normalizeTypeKey(input: String): String {
    val normalized = input.lowercase()
        .removePrefix("field")
        .removeSuffix("field")
        .trim()

    return normalized.ifEmpty { "field" }
}

