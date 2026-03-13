package com.pydantic.test

import io.kotest.matchers.shouldBe
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File

object PydanticPluginTestHelper {

    fun createTestProject(dir: File, buildScript: String, sourceFiles: Map<String, String> = emptyMap()): File {
        if (dir.exists()) dir.deleteRecursively()
        dir.mkdirs()

        // Create build.gradle.kts
        File(dir, "build.gradle.kts").writeText(buildScript)

        // Create settings.gradle.kts
        File(dir, "settings.gradle.kts").writeText("""
            rootProject.name = "test-project"
        """.trimIndent())

        // Create source files
        sourceFiles.forEach { (path, content) ->
            val file = File(dir, path)
            file.parentFile.mkdirs()
            file.writeText(content)
        }

        return dir
    }

    fun runGradleBuild(
        projectDir: File,
        vararg tasks: String,
        shouldSucceed: Boolean = true
    ): BuildResult {
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*tasks, "--stacktrace", "--info", "--no-configuration-cache")
            .withDebug(true)
            .let { runner ->
                if (shouldSucceed) runner.build() else runner.buildAndFail()
            }
    }

    fun assertTaskSuccess(result: BuildResult, taskName: String) {
        result.task(":$taskName")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    fun assertTaskFailure(result: BuildResult, taskName: String) {
        result.task(":$taskName")?.outcome shouldBe TaskOutcome.FAILED
    }
}