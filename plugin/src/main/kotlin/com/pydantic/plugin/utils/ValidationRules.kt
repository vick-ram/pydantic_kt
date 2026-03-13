package com.pydantic.plugin.utils

import com.pydantic.runtime.validation.ModelInfo
import com.pydantic.runtime.validation.PropertyInfo

data class ValidationRule(
    val constraintClass: String,
    val method: String,
    val isBooleanFlag: Boolean = false
)

object ValidationRegistry {
    private val NUMBER_TYPES = setOf(
        "int", "long", "float", "double", "bigdecimal", "biginteger"
    )

    private val STRING_TYPES = setOf("string")

    private val TEMPORAL_TYPES = setOf(
        "localdate",
        "localtime",
        "localdatetime",
        "zoneddatetime",
        "instant"
    )


    val STRING_RULES = mapOf(
        "minLength" to ValidationRule("StringConstraints", "minLength"),
        "maxLength" to ValidationRule("StringConstraints", "maxLength"),
        "pattern" to ValidationRule("StringConstraints", "pattern"),
        "startsWith" to ValidationRule("StringConstraints", "startsWith"),
        "endsWith" to ValidationRule("StringConstraints", "endsWith"),
        "contains" to ValidationRule("StringConstraints", "contains"),
        "eq" to ValidationRule("StringConstraints", "equalsTo"),
        "email" to ValidationRule("StringConstraints", "email", true),
        "url" to ValidationRule("StringConstraints", "url", true),
        "uuid" to ValidationRule("StringConstraints", "uuid", true),
        "notBlank" to ValidationRule("StringConstraints", "notBlank", true),
        "notEmpty" to ValidationRule("StringConstraints", "notEmpty", true),
        "lowercase" to ValidationRule("StringConstraints", "lowercase", true),
        "uppercase" to ValidationRule("StringConstraints", "uppercase", true),
        "slug" to ValidationRule("StringConstraints", "slug", true),
        "ascii" to ValidationRule("StringConstraints", "ascii"),
        "trimmed" to ValidationRule("StringConstraints", "trimmed"),
        "endsWith" to ValidationRule("StringConstraints", "endsWith"),
        "dateTime" to ValidationRule("DateTimeConstraints", "dateTime"),
        "date" to ValidationRule("DateTimeConstraints", "date"),
        "time" to ValidationRule("DateTimeConstraints", "time"),
    )

    val NUMBER_RULES = mapOf(
        "min" to ValidationRule("NumberConstraints", "min"),
        "max" to ValidationRule("NumberConstraints", "max"),
        "gt" to ValidationRule("NumberConstraints", "greaterThan"),
        "lt" to ValidationRule("NumberConstraints", "lessThan"),
        "eq" to ValidationRule("NumberConstraints", "equalsTo"),
        "divisibleBy" to ValidationRule("NumberConstraints", "divisibleBy"),
        "range" to ValidationRule("NumberConstraints", "range"),
    )

    val DATETIME_RULES = mapOf(
        "before" to ValidationRule("DateTimeConstraints", "before"),
        "after" to ValidationRule("DateTimeConstraints", "after"),
        "inTimeZone" to ValidationRule("DateTimeConstraints", "inTimeZone"),
        "weekday" to ValidationRule("DateTimeConstraints", "weekday", true),
        "weekend" to ValidationRule("DateTimeConstraints", "weekend", true),
        "between" to ValidationRule("DateTimeConstraints", "between"),
        "past" to ValidationRule("DateTimeConstraints", "past"),
        "future" to ValidationRule("DateTimeConstraints", "future"),
        "timeBetween" to ValidationRule("DateTimeConstraints", "timeBetween")
    )

    val STANDALONE_ANNOTATIONS = mapOf(
        "Email" to ValidationRule("StringConstraints", "email", isBooleanFlag = true),
        "Url" to ValidationRule("StringConstraints", "url", isBooleanFlag = true),
        "NotBlank" to ValidationRule("StringConstraints", "notBlank", isBooleanFlag = true),
        "NotEmpty" to ValidationRule("StringConstraints", "notEmpty", isBooleanFlag = true),
        "UUID" to ValidationRule("StringConstraints", "uuid", isBooleanFlag = true),
        "Min" to ValidationRule("NumberConstraints", "min"),
        "Max" to ValidationRule("NumberConstraints", "max")
    )

    fun getRulesForType(type: String): Map<String, ValidationRule> {
        val typeLower = type
            .removeSuffix("?")
            .substringAfterLast(".")
            .lowercase()

        return when (typeLower) {
            in STRING_TYPES -> STRING_RULES
            in NUMBER_TYPES -> NUMBER_RULES
            in TEMPORAL_TYPES -> DATETIME_RULES
            else -> emptyMap()
        }
    }
}

fun generatePropValidation(prop: PropertyInfo, model: ModelInfo): String {
    val validations = mutableListOf<String>()

    if (!prop.isNullable) {
        validations.add(generateRequiredCheck(model.name, prop.name))
    }

    prop.annotations.forEach { (groupName, constraints) ->
        when (groupName) {
            "Field", "String", "Number", "DateTime" -> {
                val rules = ValidationRegistry.getRulesForType(prop.type)
                constraints.forEach { (key, value) ->
                    rules[key]?.let { rule ->
                        validations.add(buildValidationCode(model.name, prop.name, rule, value))
                    }
                }
            }

            "Pattern" -> {
                val regex = constraints["regex"] ?: constraints["value"]
                regex?.let {
                    validations.add(
                        buildValidationCode(
                            model.name,
                            prop.name,
                            ValidationRegistry.STRING_RULES["pattern"]!!,
                            it
                        )
                    )
                }
            }

            else -> {
                ValidationRegistry.STANDALONE_ANNOTATIONS[groupName]?.let { rule ->
                    val value = constraints["value"] ?: true
                    validations.add(buildValidationCode(model.name, prop.name, rule, value))
                }
            }
        }
    }
    return validations.joinToString("\n")
}

private fun buildValidationCode(modelName: String, propName: String, rule: ValidationRule, value: Any): String {

    // Helper to format individual values (adding quotes to strings, etc.)
    fun formatValue(v: Any): String {
        val s = v.toString().trim()
        // If it looks like a String literal (not a number and not a code call like LocalDate.now())
        return if (s.startsWith("\"") || s.toDoubleOrNull() != null || s.contains("(")) {
            s
        } else {
            "\"$s\"" // Wrap raw strings in quotes
        }
    }

    val callParams = when {
        rule.isBooleanFlag -> "value"

        // Handle Multi-parameter rules (range, between, etc.)
        value is List<*> -> {
            val joinedArgs = value.filterNotNull().joinToString(", ") { formatValue(it) }
            "value, $joinedArgs"
        }

        // Handle Single-parameter rules
        else -> "value, ${formatValue(value)}"
    }

    return """
        addValidation($modelName::$propName) { value ->
            ${rule.constraintClass}.${rule.method}($callParams)?.copy(field = "$propName")
        }
    """.trimIndent()
}

private fun generateRequiredCheck(modelName: String, propName: String): String {
    return """
        addValidation($$modelName::$propName) { value ->
            if (value == null) ValidationError(
                field = "$propName",
                message = "$propName is required",
                code = "REQUIRED"
            ) else null
        }
    """.trimIndent()
}

data class DelegateRules(
    val fieldParams: Set<String>,
    val markerAnnotations: Map<String, String>
)

val DELEGATE_RULES: Map<String, DelegateRules> = mapOf(
    "string" to DelegateRules(
        fieldParams = setOf(
            "minLength",
            "maxLength",
            "pattern",
            "startsWith",
            "endsWith",
            "contains",
            "equalsTo"
        ),
        markerAnnotations = mapOf(
            "email" to "Email",
            "url" to "Url",
            "uuid" to "Uuid",
            "slug" to "Slug",
            "ascii" to "Ascii",
            "trimmed" to "Trimmed",
            "lowercase" to "Lowercase",
            "uppercase" to "Uppercase",
            "notBlank" to "NotBlank",
            "notEmpty" to "NotEmpty"
        )
    ),

    "int" to DelegateRules(
        fieldParams = setOf("min", "max", "greaterThan", "lessThan", "equalsTo", "divisibleBy"),
        markerAnnotations = emptyMap()
    ),
    "long" to DelegateRules(
        fieldParams = setOf("min", "max", "gt", "lt", "equalsTo"),
        markerAnnotations = emptyMap()
    ),
    "float" to DelegateRules(
        fieldParams = setOf("min", "max", "gt", "lt"),
        markerAnnotations = emptyMap()
    ),
    "double" to DelegateRules(
        fieldParams = setOf("min", "max", "gt", "lt"),
        markerAnnotations = emptyMap()
    ),

    // Date / time delegates
    "date" to DelegateRules(
        fieldParams = setOf("before", "after"),
        markerAnnotations = mapOf(
            "weekday" to "Weekday",
            "weekend" to "Weekend"
        )
    ),
    "time" to DelegateRules(
        fieldParams = setOf("before", "after"),
        markerAnnotations = emptyMap()
    ),
    "datetime" to DelegateRules(
        fieldParams = setOf("before", "after"),
        markerAnnotations = mapOf(
            "weekday" to "Weekday",
            "weekend" to "Weekend"
        )
    )
)

val PARAM_ALIASES = mapOf("gt" to "greaterThan", "lt" to "lessThan")

fun normalizeTypeKey(input: String): String {
    val normalized = input.lowercase()
        .removePrefix("field")
        .removeSuffix("field")
        .trim()

    return normalized.ifEmpty { "field" }
}