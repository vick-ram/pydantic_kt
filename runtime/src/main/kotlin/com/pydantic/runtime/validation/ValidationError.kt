package com.pydantic.runtime.validation

data class ValidationError(
    val field: String?,
    val message: String,
    val code: String,
    val value: Any? = null,
    val constraints: Map<String, Any> = emptyMap()
) {
    companion object {
        fun error(
            field: String?,
            code: String,
            message: String,
            value: Any? = null,
            constraints: Map<String, Any> = emptyMap()
        ) =
            ValidationError(field, message, code, value, constraints)

        fun warning(
            field: String?,
            code: String,
            message: String,
            value: Any? = null,
            constraints: Map<String, Any> = emptyMap()
        ) =
            ValidationError(field, message, code, value, constraints)
    }
}

class ValidationException(
    errors: List<ValidationError>
) : RuntimeException(
    "Validation failed:\n" +
            errors.joinToString("\n") {
                val field = it.field?.let { f -> "[$f] " } ?: ""
                "$field${it.message} (${it.code})"
            }
)
