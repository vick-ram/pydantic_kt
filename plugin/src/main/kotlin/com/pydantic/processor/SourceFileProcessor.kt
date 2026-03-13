package com.pydantic.processor

import com.pydantic.plugin.utils.extractClassContent
import com.pydantic.plugin.utils.extractDelegateProperties
import com.pydantic.plugin.utils.extractPropertiesFromAnnotations
import com.pydantic.plugin.utils.hasDelegateProperties
import com.pydantic.runtime.validation.ModelInfo
import java.io.File

class SourceFileProcessor {

    fun processFile(file: File, detectDelegates: Boolean = false): List<ModelInfo> {
        val content = file.readText()
        println("Content read: $content")
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

    // Keep all the original annotation parsing methods...
    private fun extractPackageName(content: String): String {
        val packageRegex = """(?m)^package\s+([\w.]+)""".toRegex()
        val match = packageRegex.find(content)

        return match?.groupValues?.get(1)?.trim() ?: ""
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
}

