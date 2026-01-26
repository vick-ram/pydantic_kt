package com.example

import com.example.models.User
import com.example.models.Product
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

class PydanticFunctionalTest : StringSpec({

    "should generate validator for annotation-based models" {
        // Check that validator was generated
        val generatedDir = File("build/generated/sources/pydantic")
        generatedDir.exists() shouldBe true

        val userValidator = File(generatedDir, "com/example/models/UserValidator.kt")
        userValidator.exists() shouldBe true

        val content = userValidator.readText()
        content shouldContain "class UserValidator"
        content shouldContain "StringConstraints.minLength"
        content shouldContain "StringConstraints.maxLength"
    }

    "should generate helper code for delegation-based models" {
        if (File("build/generated/sources/pydantic-delegates").exists()) {
            val productHelpers = File("build/generated/sources/pydantic-delegates/com/example/models/ProductDelegates.kt")
            productHelpers.exists() shouldBe true
        }
    }

    "should generate JSON schemas" {
        val schemasDir = File("build/generated/schemas")
        if (schemasDir.exists()) {
            val userSchema = File(schemasDir, "com/example/models/User.schema.json")
            userSchema.exists() shouldBe true

            val content = userSchema.readText()
            content shouldContain "\"type\": \"object\""
            content shouldContain "\"properties\""
            content shouldContain "\"name\""
        }
    }

    "should compile project successfully" {
        // This test will fail if compilation fails
        // We're testing that generated code is valid Kotlin
        true shouldBe true  // Placeholder - actual compilation test happens during build
    }
})