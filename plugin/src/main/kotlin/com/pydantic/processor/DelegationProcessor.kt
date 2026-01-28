package com.pydantic.processor

import com.pydantic.runtime.validation.ModelInfo
import com.pydantic.runtime.validation.PropertyInfo
import java.io.File

class DelegationProcessor {

    fun processFile(file: File): List<ModelInfo> {
        val content = file.readText()
        return extractModelsWithDelegates(content, file)
    }

    private fun extractModelsWithDelegates(content: String, file: File): List<ModelInfo> {
        val models = mutableListOf<ModelInfo>()
        val packageName = extractPackageName(content)

        // Look for classes with property delegates
        val classRegex = """class\s+(\w+)\s*[:\{]""".toRegex()

        classRegex.findAll(content).forEach { match ->
            val className = match.groupValues[1]
            val classContent = extractClassContent(content, className)

            if (classContent.lowercase().contains("by field")) {
                val properties = extractDelegateProperties(classContent)
                val modelInfo = ModelInfo(
                    name = className,
                    packageName = packageName,
                    properties = properties,
                    annotations = emptyMap(), // Not using annotations
                    isStrict = false
                )
                models.add(modelInfo)
            }
        }

        return models
    }

    private fun extractDelegateProperties(classContent: String): List<PropertyInfo> {
        val properties = mutableListOf<PropertyInfo>()

        // Match: var/val name: Type by Field.xxx(...)
        val delegateRegex = """(?:val|var)\s+(\w+)\s*:\s*([^=\n]+)\s+by\s+([a-zA-Z0-9_.]*Field|[Ff]ield)\s*(?:\(([^)]*)\)|\{([^}]*)\})""".toRegex()

        delegateRegex.findAll(classContent).forEach { match ->
            val name = match.groupValues[1]
            val type = match.groupValues[2].trim()
            val delegateCall = match.groupValues[3]
            val params = match.groupValues[4]

            // Parse delegate parameters to extract constraints
            val annotations = parseDelegateParams(params, type)

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

    private fun parseDelegateParams(
        params: String,
        type: String
    ): Map<String, Map<String, Any>> {

        val annotations = mutableMapOf<String, MutableMap<String, Any>>()

        fun add(group: String, key: String, value: Any) {
            annotations.getOrPut(group) { mutableMapOf() }[key] = value
        }

        fun has(name: String) =
            params.contains("$name(", true) || params.contains("$name()", true)

        fun split2(value: String): Pair<String, String>? {
            val parts = value.split(',').map { it.trim() }
            return if (parts.size == 2) parts[0] to parts[1] else null
        }

        // ---------- STRING ----------
        if (type.startsWith("String")) {

            extractParam(params, "minLength").toIntOrNull()
                ?.let { add("String", "minLength", it) }
            extractParam(params, "maxLength").toIntOrNull()
                ?.let { add("String", "maxLength", it) }
            add("String", "pattern", extractParam(params, "pattern").trim('"'))
            add("String", "startsWith", extractParam(params, "startsWith").trim('"'))
            add("String", "endsWith", extractParam(params, "endsWith").trim('"'))
            add("String", "contains", extractParam(params, "contains").trim('"'))
            add("String", "equals", extractParam(params, "equalsTo").trim('"'))

            if (has("email")) add("String", "email", true)
            if (has("url")) add("String", "url", true)
            if (has("uuid")) add("String", "uuid", true)
            if (has("slug")) add("String", "slug", true)
            if (has("ascii")) add("String", "ascii", true)
            if (has("trimmed")) add("String", "trimmed", true)
            if (has("lowercase")) add("String", "lowercase", true)
            if (has("uppercase")) add("String", "uppercase", true)
            if (has("notBlank")) add("String", "notBlank", true)
            if (has("notEmpty")) add("String", "notEmpty", true)
        }

        // ---------- NUMBER ----------
        if (
            type.startsWith("Int") ||
            type.startsWith("Long") ||
            type.startsWith("Float") ||
            type.startsWith("Double") ||
            type.startsWith("BigDecimal") ||
            type.startsWith("BigInteger")
        ) {

            add("Number", "min", extractParam(params, "min"))
            add("Number", "max", extractParam(params, "max"))
            add("Number", "gt", extractParam(params, "greaterThan"))
            add("Number", "lt", extractParam(params, "lessThan"))
            add("Number", "equals", extractParam(params, "equalsTo"))
            add("Number", "divisibleBy", extractParam(params, "divisibleBy"))

            // range(min, max)
            split2(extractParam(params, "range"))
                ?.let { (min, max) ->
                    add("Number", "min", min)
                    add("Number", "max", max)
                }
        }

        // ---------- DATE / TIME ----------
        if (
            type.startsWith("LocalDate") ||
            type.startsWith("LocalTime") ||
            type.startsWith("LocalDateTime") ||
            type.startsWith("ZonedDateTime") ||
            type.startsWith("Instant")
        ) {

            add("DateTime", "before", extractParam(params, "before"))
            add("DateTime", "after", extractParam(params, "after"))

            split2(extractParam(params, "between"))
                ?.let { (start, end) ->
                    add("DateTime", "start", start)
                    add("DateTime", "end", end)
                }

            split2(extractParam(params, "past"))
                ?.let { (amount, unit) ->
                    add("DateTime", "pastAmount", amount)
                    add("DateTime", "pastUnit", unit)
                }

            split2(extractParam(params, "future"))
                ?.let { (amount, unit) ->
                    add("DateTime", "futureAmount", amount)
                    add("DateTime", "futureUnit", unit)
                }

            if (has("weekday")) add("DateTime", "weekday", true)
            if (has("weekend")) add("DateTime", "weekend", true)
        }

        return annotations
    }


    private fun extractPackageName(content: String): String {
        val packageRegex = """package\s+([a-zA-Z0-9_.]+)""".toRegex()
        return packageRegex.find(content)?.groupValues?.get(1) ?: ""
    }

    private fun extractClassContent(content: String, className: String): String {
        val start = content.indexOf("class $className")
        if (start == -1) return ""

        var braceCount = 0
        var end = start

        for (i in start until content.length) {
            when (content[i]) {
                '{' -> braceCount++
                '}' -> {
                    braceCount--
                    if (braceCount == 0) {
                        end = i + 1
                        break
                    }
                }
            }
        }

        return content.substring(start, end)
    }

    private fun extractParam(params: String, paramName: String): String {
        val pattern = """$paramName(?:\s*=\s*|\s*\()([^,)\s]+)\)?""".toRegex()
        return pattern.find(params)?.groupValues?.get(1) ?: ""
    }
}