package com.pydantic.processor

import com.pydantic.runtime.validation.ModelInfo
import com.pydantic.runtime.validation.PropertyInfo
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class ValidatorGeneratorTest : StringSpec({

    val generator = ValidatorGenerator()

    "should generate validator class" {
        val model = ModelInfo(
            name = "User",
            packageName = "com.example",
            properties = listOf(
                PropertyInfo(
                    name = "name",
                    type = "String",
                    annotations = mapOf(
                        "Field" to mapOf("minLength" to 2, "maxLength" to 100)
                    ),
                    isNullable = false
                )
            ),
            annotations = emptyMap(),
            isStrict = false
        )

        val code = generator.generateValidator(model, strict = false)

        code shouldContain "class UserValidator : BaseValidator<User>()"
        code shouldContain "addValidation(User::name)"
        code shouldContain "StringConstraints.minLength"
        code shouldContain "package com.example"
    }

    "should handle nullable properties" {
        val model = ModelInfo(
            name = "Product",
            packageName = "com.example",
            properties = listOf(
                PropertyInfo(
                    name = "description",
                    type = "String?",
                    annotations = emptyMap(),
                    isNullable = true
                )
            ),
            annotations = emptyMap(),
            isStrict = false
        )

        val code = generator.generateValidator(model, strict = false)

        // Nullable properties shouldn't have required validation
        code shouldNotContain "REQUIRED"
    }

    "should generate email validation" {
        val model = ModelInfo(
            name = "Contact",
            packageName = "com.example",
            properties = listOf(
                PropertyInfo(
                    name = "email",
                    type = "String",
                    annotations = mapOf("Email" to emptyMap()),
                    isNullable = false
                )
            ),
            annotations = emptyMap(),
            isStrict = false
        )

        val code = generator.generateValidator(model, strict = false)

        code shouldContain "StringConstraints.email"
    }
})