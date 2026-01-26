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

            if (classContent.contains("by Field") || classContent.contains("by field")) {
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
        val delegateRegex = """(?:val|var)\s+(\w+)\s*:\s*([^=\n]+)\s+by\s+(Field\.\w+|[Ff]ield)\(([^)]*)\)""".toRegex()

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

    private fun parseDelegateParams(params: String, type: String): Map<String, Map<String, Any>> {
        val annotations = mutableMapOf<String, Map<String, Any>>()

        // Simple parsing - in reality would need proper parser
        if (params.contains("minLength")) {
            val minLength = extractParam(params, "minLength")
            annotations["Field"] = mapOf("minLength" to minLength.toInt())
        }

        if (params.contains("email=true")) {
            annotations["Email"] = emptyMap()
        }

        // ... parse other parameters

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
        val pattern = """$paramName\s*=\s*([^,\s]+)""".toRegex()
        return pattern.find(params)?.groupValues?.get(1) ?: ""
    }
}