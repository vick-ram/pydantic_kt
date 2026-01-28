package com.pydantic.runtime.delegates

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class FieldDelegateTest : StringSpec({

    "Field.string should create delegate with validation" {
        val delegate = Field.string(minLength = 3)
        delegate.shouldBeInstanceOf<FieldDelegate<String>>()
    }

    "Field.int should create delegate with numeric validation" {
        val delegate = Field.int(min = 0, max = 100)
        delegate.shouldBeInstanceOf<FieldDelegate<Int>>()
    }

    "field DSL should create customizable delegates" {
        val delegate = stringField {
            initial("default")
            minLength(2)
            maxLength(10)
        }
        delegate.shouldBeInstanceOf<FieldDelegate<String>>()
    }
})

// Test class using delegates
class TestUser {
    var name: String by stringField {
        minLength(2)
        maxLength(10)
    }
//    var name: String by Field.string(minLength = 2, maxLength = 10)
    var age: Int by Field.int(min = 0, max = 150)
}

class FieldDelegateIntegrationTest : StringSpec({

    "delegate should validate on assignment" {
        val user = TestUser()

        // Valid assignments
        user.name = "John"
        user.name shouldBe "John"

        user.age = 25
        user.age shouldBe 25

        // Invalid assignments should throw
        shouldThrow<IllegalArgumentException> {
            user.name = "J"  // Too short
        }

        shouldThrow<IllegalArgumentException> {
            user.age = -5  // Below min
        }

        shouldThrow<IllegalArgumentException> {
            user.age = 200  // Above max
        }
    }

    "delegate should provide default value" {
        val user = TestUser()

        // Accessing uninitialized property should throw
        shouldThrow<IllegalStateException> {
            println(user.name)
        }

        // But if we provide initial value
        class TestUser2 {
            var name: String by Field.string(initialValue = "default", minLength = 2)
        }

        val user2 = TestUser2()
        user2.name shouldBe "default"
    }
})