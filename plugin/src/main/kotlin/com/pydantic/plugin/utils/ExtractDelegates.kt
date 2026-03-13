package com.pydantic.plugin.utils

import com.pydantic.runtime.validation.PropertyInfo

fun extractDelegateProperties(classContent: String): List<PropertyInfo> {
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

        val annotations = parseGenericFieldParams(dslContent)

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

private fun parseGenericFieldParams(dslContent: String): Map<String, Map<String, Any>> {
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

// Check if class contains delegate properties
fun hasDelegateProperties(classContent: String): Boolean {
    val delegateRegexCheck by lazy {
        val typeNames = DELEGATE_RULES.keys.joinToString("|")
        """by\s+(?:Field(?:\.\w+)?|(?:$typeNames)Field|field)\b""".toRegex(RegexOption.IGNORE_CASE)
    }
    return delegateRegexCheck.containsMatchIn(classContent)
}