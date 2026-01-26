package com.pydantic.plugin

import com.pydantic.test.PydanticPluginTestHelper
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

class PydanticPluginIntegrationTest : StringSpec({

    val testProjectDir = File("build/test-projects/integration")

    beforeSpec {
        testProjectDir.deleteRecursively()
    }

    "plugin should apply successfully" {
        val buildScript = """
            plugins {
                kotlin("jvm") version "1.9.0"
                id("com.pydantic")
            }
            
            repositories {
                mavenCentral()
            }
            
            pydantic {
                enabled = true
                useDelegates = false
            }
            
            dependencies {
                implementation(kotlin("stdlib"))
            }
        """.trimIndent()

        PydanticPluginTestHelper.createTestProject(testProjectDir, buildScript)
        val result = PydanticPluginTestHelper.runGradleBuild(testProjectDir, "tasks")

        result.output shouldContain "generatePydanticValidators"
        result.output shouldContain "validatePydanticModels"
        result.output shouldContain "generateJsonSchemas"
    }

    "should generate validators for annotated models" {
        val buildScript = """
            plugins {
                kotlin("jvm") version "1.9.0"
                id("com.pydantic")
            }
            
            pydantic {
                enabled = true
                useDelegates = false
            }
        """.trimIndent()

        val sourceFiles = mapOf(
            "src/main/kotlin/com/example/User.kt" to """
                package com.example
                
                import com.pydantic.annotation.Serializable
                import com.pydantic.annotation.Field
                
                @Serializable
                data class User(
                    @Field(minLength = 2)
                    val name: String,
                    @Field(min = 0)
                    val age: Int
                )
            """.trimIndent()
        )

        PydanticPluginTestHelper.createTestProject(testProjectDir, buildScript, sourceFiles)
        val result = PydanticPluginTestHelper.runGradleBuild(
            testProjectDir,
            "generatePydanticValidators",
            "--info"
        )

        PydanticPluginTestHelper.assertTaskSuccess(result, "generatePydanticValidators")

        // Check generated file
        val generatedFile = File(testProjectDir, "build/generated/sources/pydantic/com/example/UserValidator.kt")
        generatedFile.exists() shouldBe true
        generatedFile.readText() shouldContain "class UserValidator"
    }

    "should fail validation when models are invalid" {
        val buildScript = """
            plugins {
                kotlin("jvm") version "1.9.0"
                id("com.pydantic")
            }
            
            pydantic {
                enabled = true
                useDelegates = false
                strictValidation = true
                failOnValidationError = true
            }
        """.trimIndent()

        val sourceFiles = mapOf(
            "src/main/kotlin/com/example/BadUser.kt" to """
                package com.example
                
                // Missing @Serializable annotation
                data class BadUser(
                    val name: String  // No validation annotations
                )
            """.trimIndent()
        )

        PydanticPluginTestHelper.createTestProject(testProjectDir, buildScript, sourceFiles)
        val result = PydanticPluginTestHelper.runGradleBuild(
            testProjectDir,
            "validatePydanticModels",
            shouldSucceed = false
        )

        PydanticPluginTestHelper.assertTaskFailure(result, "validatePydanticModels")
        result.output shouldContain "must be annotated with @Serializable"
    }

    "should work with delegation mode" {
        val buildScript = """
            plugins {
                kotlin("jvm") version "1.9.0"
                id("com.pydantic")
            }
            
            pydantic {
                enabled = true
                useDelegates = true
            }
            
            dependencies {
                implementation("com.pydantic:pydantic-runtime:1.0.0")
            }
        """.trimIndent()

        val sourceFiles = mapOf(
            "src/main/kotlin/com/example/Product.kt" to """
                package com.example
                
                import com.pydantic.runtime.delegates.Field
                
                class Product {
                    var name: String by Field.string(minLength = 3)
                    var price: Double by Field.double(min = 0.01)
                }
            """.trimIndent()
        )

        PydanticPluginTestHelper.createTestProject(testProjectDir, buildScript, sourceFiles)
        val result = PydanticPluginTestHelper.runGradleBuild(
            testProjectDir,
            "generatePydanticDelegates"
        )

        PydanticPluginTestHelper.assertTaskSuccess(result, "generatePydanticDelegates")
    }
})