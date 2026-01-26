package com.pydantic.plugin.tasks

import com.pydantic.processor.DelegationGenerator
import com.pydantic.processor.DelegationProcessor
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.Incremental
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskAction
import org.gradle.work.InputChanges
import java.io.File

@CacheableTask
abstract class GenerateDelegatesTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Incremental
    abstract val inputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val packageName: Property<String>

    init {
        inputDir.convention(project.layout.projectDirectory.dir("src/main/kotlin"))
        outputDir.convention(project.layout.buildDirectory.dir("generated/sources/pydantic-delegates"))
        packageName.convention("com.pydantic.generated.delegates")
    }

    @TaskAction
    fun generate(inputChanges: InputChanges) {
        val processor = DelegationProcessor()
        val generator = DelegationGenerator()

        inputChanges.getFileChanges(inputDir).forEach { change ->
            if (change.file.isFile && change.file.extension == "kt") {
                try {
                    val models = processor.processFile(change.file)
                    models.forEach { model ->
                        val delegateCode = generator.generateDelegates(model)
                        val outputFile = File(
                            outputDir.get().asFile,
                            "${model.packageName.replace('.', '/')}/${model.name}Delegates.kt"
                        )

                        outputFile.parentFile.mkdirs()
                        outputFile.writeText(delegateCode)
                    }
                } catch (e: Exception) {
                    logger.error("Failed to process file", e)
                }
            }
        }
    }
}