package com.pydantic.runtime.validation.constraints

import com.pydantic.runtime.validation.ValidationError

object StringConstraints {

    fun minLength(value: String?, min: Int): ValidationError? {
        return if (value != null && value.length < min) {
            ValidationError(
                field = null,
                message = "Value must be at least $min characters long",
                code = "STRING_MIN_LENGTH",
                value = value,
                constraints = mapOf("minLength" to min, "actualLength" to value.length)
            )
        } else null
    }

    fun maxLength(value: String?, max: Int): ValidationError? {
        return if (value != null && value.length > max) {
            ValidationError(
                field = null,
                message = "Value must be at most $max characters long",
                code = "STRING_MAX_LENGTH",
                value = value,
                constraints = mapOf("maxLength" to max, "actualLength" to value.length)
            )
        } else null
    }

    fun pattern(value: String?, regex: String): ValidationError? {
        return if (value != null && !regex.toRegex().matches(value)) {
            ValidationError(
                field = "",
                message = "Value must match pattern: $regex",
                code = "STRING_PATTERN",
                value = value,
                constraints = mapOf("pattern" to regex)
            )
        } else null
    }

    fun email(value: String?): ValidationError? {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\$".toRegex()
        return if (value != null && !emailRegex.matches(value)) {
            ValidationError(
                field = "",
                message = "Invalid email address format",
                code = "STRING_EMAIL",
                value = value,
                constraints = mapOf("pattern" to emailRegex.pattern)
            )
        } else null
    }

    fun url(value: String?): ValidationError? {
        val urlRegex = "^(https?|ftp)://[^\\s/$.?#].[^\\s]*\$".toRegex()
        return if (value != null && !urlRegex.matches(value)) {
            ValidationError(
                field = "",
                message = "Invalid URL format",
                code = "STRING_URL",
                value = value,
                constraints = mapOf("pattern" to urlRegex.pattern)
            )
        } else null
    }

    fun uuid(value: String?): ValidationError? {
        val uuidRegex = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\$".toRegex()
        return if (value != null && !uuidRegex.matches(value)) {
            ValidationError(
                field = "",
                message = "Invalid UUID format",
                code = "STRING_UUID",
                value = value,
                constraints = mapOf("pattern" to uuidRegex.pattern)
            )
        } else null
    }

    fun notBlank(value: String?): ValidationError? {
        return if (value != null && value.isBlank()) {
            ValidationError(
                field = "",
                message = "Value must not be blank",
                code = "STRING_NOT_BLANK",
                value = value
            )
        } else null
    }

    fun notEmpty(value: String?): ValidationError? {
        return if (value != null && value.isEmpty()) {
            ValidationError(
                field = "",
                message = "Value must not be empty",
                code = "STRING_NOT_EMPTY",
                value = value
            )
        } else null
    }

    fun startsWith(value: String?, prefix: String): ValidationError? {
        return if (value != null && !value.startsWith(prefix)) {
            ValidationError(
                field = "",
                message = "Value must start with '$prefix'",
                code = "STRING_STARTS_WITH",
                value = value,
                constraints = mapOf("prefix" to prefix)
            )
        } else null
    }

    fun endsWith(value: String?, suffix: String): ValidationError? {
        return if (value != null && !value.endsWith(suffix)) {
            ValidationError(
                field = "",
                message = "Value must end with '$suffix'",
                code = "STRING_ENDS_WITH",
                value = value,
                constraints = mapOf("suffix" to suffix)
            )
        } else null
    }

    fun contains(value: String?, substring: String): ValidationError? {
        return if (value != null && !value.contains(substring)) {
            ValidationError(
                field = "",
                message = "Value must contain '$substring'",
                code = "STRING_CONTAINS",
                value = value,
                constraints = mapOf("substring" to substring)
            )
        } else null
    }

    fun equalsIgnoreCase(value: String?, other: String): ValidationError? {
        return if (value != null && !value.equals(other, ignoreCase = true)) {
            ValidationError(
                field = "",
                message = "Value must equal '$other' (case insensitive)",
                code = "STRING_EQUALS_IGNORE_CASE",
                value = value,
                constraints = mapOf("expected" to other)
            )
        } else null
    }

    fun oneOf(value: String?, allowed: Set<String>, ignoreCase: Boolean = false): ValidationError? {
        return if (value != null) {
            val matches = if (ignoreCase) {
                allowed.any { it.equals(value, ignoreCase = true) }
            } else {
                value in allowed
            }

            if (!matches) {
                ValidationError(
                    field = "",
                    message = "Value must be one of: ${allowed.joinToString(", ")}",
                    code = "STRING_ONE_OF",
                    value = value,
                    constraints = mapOf("allowedValues" to allowed)
                )
            } else null
        } else null
    }
}