package com.pydantic.runtime.validation.constraints

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class StringConstraintsTest : StringSpec({

    "minLength should return error when string is too short" {
        val result = StringConstraints.minLength("ab", 3)
        result shouldNotBe null
        result?.code shouldBe "STRING_MIN_LENGTH"
        result?.constraints?.get("minLength") shouldBe 3
        result?.constraints?.get("actualLength") shouldBe 2
    }

    "minLength should return null when string meets requirement" {
        val result = StringConstraints.minLength("abc", 3)
        result shouldBe null
    }

    "minLength should return null for null value" {
        val result = StringConstraints.minLength(null, 3)
        result shouldBe null
    }

    "maxLength should return error when string is too long" {
        val result = StringConstraints.maxLength("abcd", 3)
        result shouldNotBe null
        result?.code shouldBe "STRING_MAX_LENGTH"
    }

    "email should validate correct email format" {
        StringConstraints.email("test@example.com") shouldBe null
        StringConstraints.email("invalid-email") shouldNotBe null
        StringConstraints.email("") shouldNotBe null
    }

    "pattern should validate regex patterns" {
        val pattern = "^[A-Z][a-z]+$"
        StringConstraints.pattern("John", pattern) shouldBe null
        StringConstraints.pattern("john", pattern) shouldNotBe null
        StringConstraints.pattern("", pattern) shouldNotBe null
    }

    "notBlank should reject blank strings" {
        StringConstraints.notBlank("") shouldNotBe null
        StringConstraints.notBlank("   ") shouldNotBe null
        StringConstraints.notBlank("a") shouldBe null
        StringConstraints.notBlank(null) shouldBe null
    }

    "notEmpty should reject empty strings" {
        StringConstraints.notEmpty("") shouldNotBe null
        StringConstraints.notEmpty("   ") shouldBe null  // Not empty, has spaces
        StringConstraints.notEmpty("a") shouldBe null
    }
})