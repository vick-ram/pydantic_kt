package com.pydantic.plugin.tasks

import com.pydantic.processor.SourceFileProcessor
import com.pydantic.processor.ValidatorGenerator
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File

@CacheableTask
abstract class GenerateValidatorsTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Incremental
    abstract val inputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val strictMode: Property<Boolean>

    @get:Input
    abstract val enableKsp: Property<Boolean>

    @get:Input
    abstract val useDelegates: Property<Boolean>

    init {
        inputDir.convention(project.layout.projectDirectory.dir("src/main/kotlin"))
        outputDir.convention(project.layout.buildDirectory.dir("generated/sources/pydantic"))
        packageName.convention("com.pydantic.generated")
        strictMode.convention(false)
        enableKsp.convention(true)
        useDelegates.convention(false)
    }

    @TaskAction
    fun generate(inputChanges: InputChanges) {
        val generator = ValidatorGenerator()
        val processor = SourceFileProcessor()


        val detectDelegates = useDelegates.get()

        // Track processed models to avoid duplicates
        val processedModels = mutableSetOf<String>()

        inputChanges.getFileChanges(inputDir).forEach { change ->
            if (change.file.isFile && change.file.extension == "kt") {
                try {
                    val models = processor.processFile(change.file, detectDelegates)
                    models.forEach { model ->
                        val modelKey = "${model.packageName}.${model.name}"
                        if (modelKey !in processedModels) {
                            // Apply strict mode from task if not set in annotation
                            val useStrict = model.isStrict || strictMode.get()

                            val validatorCode = generator.generateValidator(model, useStrict)
                            validatorCode.writeTo(outputDir.get().asFile)
                            processedModels.add(modelKey)

                            logger.debug("Generated validator for ${model.name}")
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Failed to process file ${change.file.name}: ${e.message}", e)
                }
            }
        }

        // Generate index of generated validators
        generateIndexFile(processedModels)

        logger.lifecycle("Generated ${processedModels.size} validators in ${outputDir.get()}")
    }

    private fun generateIndexFile(models: Set<String>) {
        val indexFile = File(outputDir.get().asFile, "validators-index.json")
        val indexContent = """
            {
                "generatedAt": "${java.time.Instant.now()}",
                "totalValidators": ${models.size},
                "validators": [
                    ${models.joinToString(",\n    ") { "\"$it\"" }}
                ]
            }
        """.trimIndent()

        indexFile.parentFile.mkdirs()
        indexFile.writeText(indexContent)
    }
}