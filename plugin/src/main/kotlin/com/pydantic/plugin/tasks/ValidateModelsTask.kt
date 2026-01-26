package com.pydantic.plugin.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.pydantic.runtime.validation.ModelDefinition
import com.pydantic.runtime.validation.ModelValidator
import com.pydantic.runtime.validation.ValidationError
import com.pydantic.runtime.validation.ValidationResult
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.options.Option
import java.io.File

@CacheableTask
abstract class ValidateModelsTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDir: DirectoryProperty

    @get:OutputFile
    abstract val reportFile: DirectoryProperty

    @get:Input
    abstract val strictMode: Property<Boolean>

    @get:Input
    abstract val failOnError: Property<Boolean>

    @Option(option = "models", description = "Specific models to validate")
    var modelFilter: String? = null

    init {
        sourceDir.convention(project.layout.projectDirectory.dir("src/main/kotlin"))
        reportFile.convention(project.layout.buildDirectory.dir("reports/pydantic"))
        strictMode.convention(false)
        failOnError.convention(true)
    }

    @TaskAction
    fun validate() {
        val validator = ModelValidator(strictMode.get())
        val reportDir = reportFile.get().asFile
        reportDir.mkdirs()

        val validationResults = mutableListOf<com.pydantic.runtime.validation.ValidationResult>()
        val modelPattern = modelFilter?.split(",")?.toSet()

        sourceDir.get().asFile.walk()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { sourceFile ->
                val models = extractModels(sourceFile)
                models.filter { model ->
                    modelPattern == null || model.name in modelPattern
                }.forEach { model ->
                    try {
                        val result = validator.validateModel(model)
                        validationResults.add(result)

                        if (!result.isValid) {
                            logger.warn("Validation failed for ${model.name}:")
                            result.errors.forEach { error ->
                                logger.warn("  - ${error.field}: ${error.message}")
                            }
                        }
                    } catch (e: Exception) {
                        validationResults.add(
                            ValidationResult(
                                modelName = model.name,
                                isValid = false,
                                errors = listOf(
                                    ValidationError(
                                        field = model.name,
                                        message = e.message.toString(),
                                        code = "ERROR"
                                    )
                                ),
                                warnings = emptyList()
                            )
                        )
                    }
                }
            }

        generateReport(validationResults)

        val failedCount = validationResults.count { !it.isValid }
        if (failedCount > 0) {
            val message = "Found $failedCount model(s) with validation errors"
            if (failOnError.get()) {
                throw GradleException(message)
            } else {
                logger.warn(message)
            }
        }
    }

    private fun extractModels(file: File): List<ModelDefinition> {
        val content = file.readText()
        val models = mutableListOf<ModelDefinition>()

        // Look for @Serializable annotations
        val pattern = """@Serializable[^)]*\)\s*(?:data\s+)?class\s+(\w+)""".toRegex()
        val matches = pattern.findAll(content)

        matches.forEach { match ->
            val className = match.groupValues[1]
            val modelContent = extractClassContent(content, className)

            models.add(
                ModelDefinition(
                    name = className,
                    content = modelContent,
                    sourceFile = file.name
                )
            )
        }

        return models
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

    private fun generateReport(results: List<ValidationResult>) {
        val reportDir = reportFile.get().asFile
        val jsonReport = File(reportDir, "validation-report.json")
        val htmlReport = File(reportDir, "validation-report.html")

        // Generate JSON report
        val reportData = mapOf(
            "timestamp" to System.currentTimeMillis(),
            "totalModels" to results.size,
            "validModels" to results.count { it.isValid },
            "invalidModels" to results.count { !it.isValid },
            "results" to results.map { result ->
                mapOf(
                    "modelName" to result.modelName,
                    "isValid" to result.isValid,
                    "errors" to result.errors,
                    "warnings" to result.warnings
                )
            }
        )

        ObjectMapper().writerWithDefaultPrettyPrinter()
            .writeValue(jsonReport, reportData)

        // Generate HTML report
        generateHtmlReport(htmlReport, results)

        logger.lifecycle("Validation report generated: ${jsonReport.path}")
    }

    private fun generateHtmlReport(file: File, results: List<com.pydantic.runtime.validation.ValidationResult>) {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Pydantic Validation Report</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; }
                    .header { background: #f0f0f0; padding: 20px; border-radius: 5px; }
                    .summary { margin: 20px 0; }
                    .model { border: 1px solid #ddd; margin: 10px 0; padding: 15px; border-radius: 5px; }
                    .valid { border-left: 5px solid #4CAF50; }
                    .invalid { border-left: 5px solid #f44336; }
                    .error { color: #f44336; margin: 5px 0; padding-left: 20px; }
                    .warning { color: #ff9800; margin: 5px 0; padding-left: 20px; }
                    .timestamp { color: #666; font-size: 0.9em; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>Pydantic Validation Report</h1>
                    <div class="timestamp">Generated at: ${java.time.LocalDateTime.now()}</div>
                </div>
                
                <div class="summary">
                    <h2>Summary</h2>
                    <p>Total Models: ${results.size}</p>
                    <p>Valid Models: ${results.count { it.isValid }}</p>
                    <p>Invalid Models: ${results.count { !it.isValid }}</p>
                </div>
                
                <h2>Model Details</h2>
                ${results.joinToString("\n") { result ->
            """
                    <div class="model ${if (result.isValid) "valid" else "invalid"}">
                        <h3>${result.modelName} ${if (result.isValid) "✓" else "✗"}</h3>
                        ${if (result.errors.isNotEmpty()) "<h4>Errors:</h4>" + result.errors.joinToString("") { "<div class='error'>$it</div>" } else ""}
                        ${if (result.warnings.isNotEmpty()) "<h4>Warnings:</h4>" + result.warnings.joinToString("") { "<div class='warning'>$it</div>" } else ""}
                    </div>
                    """
        }}
            </body>
            </html>
        """.trimIndent()

        file.writeText(html)
    }
}