package com.pydantic.plugin.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.pydantic.processor.SchemaGenerator
import java.io.File

@CacheableTask
abstract class GenerateSchemasTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val schemaFormats: ListProperty<String>

    @get:Input
    abstract val packageName: Property<String>

    init {
        inputDir.convention(project.layout.buildDirectory.dir("generated/sources/pydantic"))
        outputDir.convention(project.layout.buildDirectory.dir("generated/schemas"))
        schemaFormats.convention(listOf("json", "yaml"))
        packageName.convention("com.pydantic.generated")
    }

    @TaskAction
    fun generate() {
        val schemaGenerator = SchemaGenerator()
        val jsonMapper = ObjectMapper()
        val yamlMapper = ObjectMapper(YAMLFactory())

        // Scan for generated validators
        inputDir.get().asFile.walk()
            .filter { it.isFile && it.extension == "kt" && it.name.endsWith("Validator.kt") }
            .forEach { validatorFile ->
                val className = validatorFile.nameWithoutExtension.removeSuffix("Validator")
                val packagePath = extractPackage(validatorFile)

                // Generate schema
                val schema = schemaGenerator.generateSchema(className, packagePath)

                // Write in multiple formats
                schemaFormats.get().forEach { format ->
                    val outputFile = when (format.lowercase()) {
                        "json" -> {
                            val json = jsonMapper.writerWithDefaultPrettyPrinter()
                                .writeValueAsString(schema)
                            File(outputDir.get().asFile, "$packagePath/$className.schema.json")
                                .apply { writeText(json) }
                        }
                        "yaml" -> {
                            val yaml = yamlMapper.writeValueAsString(schema)
                            File(outputDir.get().asFile, "$packagePath/$className.schema.yaml")
                                .apply { writeText(yaml) }
                        }
                        else -> throw IllegalArgumentException("Unsupported schema format: $format")
                    }

                    outputFile.parentFile.mkdirs()
                    logger.lifecycle("Generated ${format.uppercase()} schema: ${outputFile.path}")
                }
            }

        // Generate index of all schemas
        generateSchemaIndex()
    }

    private fun extractPackage(file: File): String {
        val content = file.readText()
        val packageRegex = """package\s+([a-zA-Z0-9_.]+)""".toRegex()
        return packageRegex.find(content)?.groupValues?.get(1)?.replace('.', '/') ?: ""
    }

    private fun generateSchemaIndex() {
        val schemas = mutableMapOf<String, Any>()

        outputDir.get().asFile.walk()
            .filter { it.isFile && (it.extension == "json" || it.extension == "yaml") }
            .forEach { schemaFile ->
                val relativePath = outputDir.get().asFile.toPath()
                    .relativize(schemaFile.toPath())
                    .toString()
                schemas[relativePath] = mapOf(
                    "type" to schemaFile.extension,
                    "size" to schemaFile.length(),
                    "lastModified" to schemaFile.lastModified()
                )
            }

        val indexFile = File(outputDir.get().asFile, "schemas-index.json")
        ObjectMapper().writerWithDefaultPrettyPrinter()
            .writeValue(indexFile, mapOf(
                "generatedAt" to System.currentTimeMillis(),
                "totalSchemas" to schemas.size,
                "schemas" to schemas
            ))
    }
}