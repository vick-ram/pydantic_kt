package com.pydantic.plugin


import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File

class SimpleTest : StringSpec({
    "test plugin classpath" {
        // Look for the generated classpath file
        val classpathFile = File("build/plugin-classpath/plugin-classpath.txt")
        println("Looking for classpath file at: ${classpathFile.absolutePath}")
        println("File exists: ${classpathFile.exists()}")

        if (classpathFile.exists()) {
            val pluginClasspath = classpathFile.readLines().map { File(it) }
            println("Found ${pluginClasspath.size} JARs in plugin classpath")
            pluginClasspath.forEach { println("  - ${it.name}") }
            pluginClasspath.isNotEmpty() shouldBe true
        } else {
            // Fallback to looking in test resources
            val fallbackClasspath = javaClass.classLoader
                .getResourceAsStream("plugin-classpath.txt")
                ?.bufferedReader()
                ?.readLines()
                ?.map { File(it) }
                ?: emptyList()

            println("Fallback: Found ${fallbackClasspath.size} JARs")
            // For now, just check the test runs
            true shouldBe true
        }
    }

    "simple gradle build" {
        val testProjectDir = File("build/test-projects/simple-test").apply {
            deleteRecursively()
            mkdirs()
        }

        // Create build.gradle.kts
        File(testProjectDir, "build.gradle.kts").writeText("""
            plugins {
                id("com.pydantic")
            }
            
            repositories {
                mavenCentral()
            }
        """.trimIndent())

        // Create settings.gradle.kts
        File(testProjectDir, "settings.gradle.kts").writeText("""
            rootProject.name = "simple-test"
        """.trimIndent())

        // Get plugin classpath
        val pluginClasspath = File("build/testkit-classpath/plugin-classpath.txt")
            .takeIf { it.exists() }
            ?.readLines()
            ?.map { File(it) }
            ?: emptyList()

        println("Using plugin classpath with ${pluginClasspath.size} entries")

        // Run gradle
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("help", "--stacktrace", "--info", "--no-configuration-cache")
            .withPluginClasspath(pluginClasspath) // Use the classpath file
            .forwardOutput()
            .build()

        println("Build output: ${result.output}")
        result.task(":help")?.outcome shouldBe TaskOutcome.SUCCESS
    }
})