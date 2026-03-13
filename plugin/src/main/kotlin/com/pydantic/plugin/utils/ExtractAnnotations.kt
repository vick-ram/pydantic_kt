package com.pydantic.plugin.utils

import com.pydantic.runtime.validation.PropertyInfo

fun extractPropertiesFromAnnotations(classContent: String): List<PropertyInfo> {
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