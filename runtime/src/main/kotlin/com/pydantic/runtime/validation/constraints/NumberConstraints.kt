package com.pydantic.runtime.validation.constraints

import com.pydantic.runtime.validation.ValidationError
import java.math.BigDecimal

object NumberConstraints {

    fun <T> min(value: T?, min: T): ValidationError? where T : Number, T : Comparable<T> {
        return if (value != null && value < min) {
            ValidationError(
                field = "",
                message = "Value must be at least $min",
                code = "NUMBER_MIN",
                value = value,
                constraints = mapOf("min" to min, "actual" to value)
            )
        } else null
    }

    fun <T> max(value: T?, max: T): ValidationError? where T : Number, T : Comparable<T> {
        return if (value != null && value > max) {
            ValidationError(
                field = "",
                message = "Value must be at most $max",
                code = "NUMBER_MAX",
                value = value,
                constraints = mapOf("max" to max, "actual" to value)
            )
        } else null
    }

    fun <T> range(value: T?, min: T, max: T): ValidationError? where T : Number, T : Comparable<T> {
        return if (value != null && (value !in min..max)) {
            ValidationError(
                field = "",
                message = "Value must be between $min and $max",
                code = "NUMBER_RANGE",
                value = value,
                constraints = mapOf("min" to min, "max" to max, "actual" to value)
            )
        } else null
    }

    fun <T> positive(value: T?): ValidationError? where T : Number, T : Comparable<T> {
        return if (value != null && value <= BigDecimal.ZERO as T) {
            ValidationError(
                field = "",
                message = "Value must be positive",
                code = "NUMBER_POSITIVE",
                value = value
            )
        } else null
    }

    fun <T> negative(value: T?): ValidationError? where T : Number, T : Comparable<T> {
        @Suppress("UNCHECKED_CAST")
        return if (value != null && value >= BigDecimal.ZERO as T) {
            ValidationError(
                field = "",
                message = "Value must be negative",
                code = "NUMBER_NEGATIVE",
                value = value
            )
        } else null
    }

    fun <T : Number> multipleOf(value: T?, factor: T): ValidationError? {
        return if (value != null) {
            val bigValue = BigDecimal(value.toString())
            val bigFactor = BigDecimal(factor.toString())

            if (bigValue.remainder(bigFactor).compareTo(BigDecimal.ZERO) != 0) {
                ValidationError(
                    field = "",
                    message = "Value must be a multiple of $factor",
                    code = "NUMBER_MULTIPLE_OF",
                    value = value,
                    constraints = mapOf("factor" to factor)
                )
            } else null
        } else null
    }

    fun <T : Number> finite(value: T?): ValidationError? {
        return if (value != null && value.toDouble().isInfinite()) {
            ValidationError(
                field = "",
                message = "Value must be finite",
                code = "NUMBER_FINITE",
                value = value
            )
        } else null
    }

    fun <T : Number> finiteOrNull(value: T?): ValidationError? {
        return if (value != null && value.toDouble().isInfinite()) {
            ValidationError(
                field = "",
                message = "Value must be finite or null",
                code = "NUMBER_FINITE_OR_NULL",
                value = value
            )
        } else null
    }

    fun <T> greaterThan(value: T?, other: T): ValidationError? where T : Number, T : Comparable<T> {
        return if (value != null && value <= other) {
            ValidationError(
                field = "",
                message = "Value must be greater than $other",
                code = "NUMBER_GREATER_THAN",
                value = value,
                constraints = mapOf("other" to other)
            )
        } else null
    }

    fun <T> lessThan(value: T?, other: T): ValidationError? where T : Number, T : Comparable<T> {
        return if (value != null && value >= other) {
            ValidationError(
                field = "",
                message = "Value must be less than $other",
                code = "NUMBER_LESS_THAN",
                value = value,
                constraints = mapOf("other" to other)
            )
        } else null
    }

    fun <T : Number> equals(value: T?, expected: T): ValidationError? {
        return if (value != null && value != expected) {
            ValidationError(
                field = "",
                message = "Value must equal $expected",
                code = "NUMBER_EQUALS",
                value = value,
                constraints = mapOf("expected" to expected)
            )
        } else null
    }

    fun <T : Number> notEquals(value: T?, notExpected: T): ValidationError? {
        return if (value != null && value == notExpected) {
            ValidationError(
                field = "",
                message = "Value must not equal $notExpected",
                code = "NUMBER_NOT_EQUALS",
                value = value,
                constraints = mapOf("notExpected" to notExpected)
            )
        } else null
    }

    fun <T : Number> oneOf(value: T?, allowed: Set<T>): ValidationError? {
        return if (value != null && value !in allowed) {
            ValidationError(
                field = "",
                message = "Value must be one of: ${allowed.joinToString(", ")}",
                code = "NUMBER_ONE_OF",
                value = value,
                constraints = mapOf("allowedValues" to allowed)
            )
        } else null
    }

    fun <T : Number> divisibleBy(value: T?, divisor: T): ValidationError? {
        return multipleOf(value, divisor)
    }
}