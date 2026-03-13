package com.pydantic.plugin

import com.pydantic.plugin.tasks.GenerateDelegatesTask
import com.pydantic.plugin.tasks.GenerateSchemasTask
import com.pydantic.plugin.tasks.GenerateValidatorsTask
import com.pydantic.plugin.tasks.ValidateModelsTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class PydanticPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Create extension
        val extension = project.extensions.create("pydantic", PydanticExtension::class.java)
        // Apply Java plugin for source sets
        project.plugins.apply("java")
        project.plugins.apply("org.jetbrains.kotlin.jvm")

        project.dependencies.add("implementation", "com.pydantic:pydantic-runtime:${project.version}")

        val generateTask = project.tasks.register("generatePydanticValidators", GenerateValidatorsTask::class.java) {
            it.group = "pydantic"
            it.description = "Generates validation code for Pydantic models"

            it.strictMode.set(extension.strictValidation)
            it.packageName.set(extension.validationPackage)
            it.useDelegates.set(extension.useDelegates)

            it.onlyIf { extension.enabled.get() }
        }

        val delegatesTask = project.tasks.register("generatePydanticDelegates", GenerateDelegatesTask::class.java) {
            it.group = "pydantic"
            it.description = "Generates helper code for Field-delegated models"
            it.onlyIf { extension.enabled.get() && extension.useDelegates.get() }
        }

        val validateTask = project.tasks.register("validatePydanticModels", ValidateModelsTask::class.java) {
            it.group = "pydantic"
            it.description = "Validates Pydantic model definitions"

            if (extension.useDelegates.get()) {
                it.dependsOn(delegatesTask)
            } else {
                it.dependsOn(generateTask)
            }
            it.strictMode.set(extension.strictValidation)
            it.failOnError.set(extension.failOnValidationError)

            it.enabled = extension.enabled.get()
        }

        project.tasks.register("generateJsonSchemas", GenerateSchemasTask::class.java) {
            it.group = "pydantic"
            it.description = "Generates JSON schemas from Pydantic models"

            it.dependsOn(validateTask)
            it.schemaFormats.set(extension.schemaFormats)

            it.enabled = extension.enabled.get() && extension.generateSchemas.get()
        }

        // Configure KSP if enabled
        if (project.extensions.findByName("ksp") != null) {
            configureKsp(project, extension)
        }

        // Hook into build process
        project.afterEvaluate {
//            val genDir = project.layout.buildDirectory.dir("generated/sources/pydantic").get()
            val generatedDir = if (extension.useDelegates.get()) {
                project.layout.buildDirectory.dir("generated/sources/pydantic-delegates").get()
            } else {
                project.layout.buildDirectory.dir("generated/sources/pydantic").get()
            }

            project.extensions.getByType(KotlinJvmProjectExtension::class.java).sourceSets.getByName("main").kotlin.srcDir(generatedDir)

            // Add generated sources as implementation dependency
            project.dependencies.add("implementation", project.files(generatedDir))

            // Make compile tasks depend on appropriate generation task
            val compileDependsOn = if (extension.useDelegates.get()) delegatesTask else generateTask

            project.tasks.withType(KotlinCompile::class.java).configureEach {
                it.dependsOn(compileDependsOn)
            }

            // Also add to Java source sets for mixed projects
            project.extensions.getByType(SourceSetContainer::class.java).getByName("main").java.srcDir(generatedDir)
        }
    }
    private fun configureKsp(project: Project, extension: PydanticExtension) {
        project.dependencies.add("ksp", project.files(project.projectDir))

        // Configure KSP to use our processor
        project.extensions.configure<Any>("ksp") {
            // This depends on how KSP is configured in the project
            // Typically you would add arguments or configuration here
        }
    }
}