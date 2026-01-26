package com.pydantic.processor

import com.pydantic.runtime.validation.ModelInfo
import com.pydantic.runtime.validation.PropertyInfo
import java.io.File

class SourceFileProcessor {

    fun processFile(file: File): List<ModelInfo> {
        val content = file.readText()
        return extractModels(content, file)
    }

    private fun extractModels(content: String, file: File): List<ModelInfo> {
        val models = mutableListOf<ModelInfo>()

        // Extract package name
        val packageName = extractPackageName(content)

        // Find all classes with @Serializable annotation
        val classRegex = """@Serializable[^)]*\)\s*(?:data\s+)?class\s+(\w+)""".toRegex()
        val classMatches = classRegex.findAll(content)

        classMatches.forEach { match ->
            val className = match.groupValues[1]
            val classContent = extractClassContent(content, className)

            if (classContent.isNotEmpty()) {
                val modelInfo = parseClass(className, packageName, classContent, file)
                models.add(modelInfo)
            }
        }

        return models
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

    private fun parseClass(
        className: String,
        packageName: String,
        classContent: String,
        sourceFile: File
    ): ModelInfo {
        // Extract @Serializable annotation parameters
        val serializableMatch = """@Serializable\(([^)]*)\)""".toRegex().find(classContent)
        val annotations = mutableMapOf<String, String>()
        var isStrict = false

        serializableMatch?.groupValues?.get(1)?.let { params ->
            params.split(',').forEach { param ->
                val keyValue = param.trim().split('=')
                if (keyValue.size == 2) {
                    val key = keyValue[0].trim()
                    val value = keyValue[1].trim()
                    annotations[key] = value

                    if (key == "strict" && value == "true") {
                        isStrict = true
                    }
                }
            }
        }

        // Extract properties
        val properties = extractProperties(classContent)

        return ModelInfo(
            name = className,
            packageName = packageName,
            properties = properties,
            annotations = annotations,
            isStrict = isStrict
        )
    }

    private fun extractProperties(classContent: String): List<PropertyInfo> {
        val properties = mutableListOf<PropertyInfo>()

        // Extract constructor parameters (for data classes)
        val constructorRegex = """(?:class|data class)\s+\w+\s*\(([^)]*)\)""".toRegex()
        val constructorMatch = constructorRegex.find(classContent)

        constructorMatch?.groupValues?.get(1)?.let { params ->
            val paramList = splitParams(params)

            paramList.forEach { param ->
                val property = parseProperty(param.trim())
                if (property != null) {
                    properties.add(property)
                }
            }
        }

        // Also look for properties in class body
        val bodyRegex = """\{([^}]*)\}""".toRegex()
        val bodyMatch = bodyRegex.find(classContent)

        bodyMatch?.groupValues?.get(1)?.let { body ->
            val propertyRegex = """(?:val|var)\s+(\w+)\s*:\s*([^=\n;]+)(?:\s*=\s*[^;\n]+)?""".toRegex()
            val propertyMatches = propertyRegex.findAll(body)

            propertyMatches.forEach { match ->
                val name = match.groupValues[1]
                val type = match.groupValues[2].trim()
                val annotations = extractPropertyAnnotations(body, name)

                properties.add(
                    PropertyInfo(
                        name = name,
                        type = type,
                        annotations = annotations,
                        isNullable = type.endsWith('?')
                    )
                )
            }
        }

        return properties
    }

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
        // Pattern: annotations? val/var name: type = defaultValue?
        val pattern = """^(.*?)\s+(val|var)\s+(\w+)\s*:\s*([^=\n]+)(?:\s*=\s*(.*))?$""".toRegex()
        val match = pattern.find(param) ?: return null

        val annotationsText = match.groupValues[1].trim()
        val name = match.groupValues[3]
        val type = match.groupValues[4].trim()
        val defaultValue = match.groupValues[5].takeIf { it.isNotEmpty() }

        val annotations = parseAnnotations(annotationsText)

        return PropertyInfo(
            name = name,
            type = type,
            annotations = annotations,
            isNullable = type.endsWith('?'),
            // We could extract defaultValue if needed
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

    private fun extractPropertyAnnotations(body: String, propertyName: String): Map<String, Map<String, Any>> {
        // Find the line with the property
        val lines = body.lines()
        for (line in lines) {
            if (line.contains("val $propertyName") || line.contains("var $propertyName")) {
                // Extract annotations from this line
                val beforeProp = line.substringBefore("val $propertyName").substringBefore("var $propertyName")
                return parseAnnotations(beforeProp)
            }
        }
        return emptyMap()
    }
}