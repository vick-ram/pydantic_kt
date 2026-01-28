package com.pydantic.processor

import com.pydantic.runtime.validation.ModelInfo
import com.pydantic.runtime.validation.PropertyInfo

class ValidatorGenerator {

    fun generateValidator(model: ModelInfo, strict: Boolean): String {
        return """
            package ${model.packageName}
            
            import com.pydantic.runtime.validation.*
            import com.pydantic.runtime.validation.constraints.*
            import java.time.*
            import kotlin.reflect.KClass
            
            class ${model.name}Validator : BaseValidator<${model.name}>() {
                
                init {
                    ${generateValidations(model)}
                }
                
                override fun getSchema(): Map<String, Any> {
                    return mapOf(
                        ${generateSchema(model)}
                    )
                }
            }
            
            fun ${model.name}.validate(): ValidationResult {
                return ${model.name}Validator().validate(this)
            }
            
            fun ${model.name}.validatePartial(): ValidationResult {
                return ${model.name}Validator().validatePartial(this)
            }
        """.trimIndent()
    }

    private fun generateValidations(model: ModelInfo): String {
        return model.properties.joinToString("\n") { prop ->
            generatePropertyValidation(prop, model)
        }
    }

    private fun generatePropertyValidation(prop: PropertyInfo, model: ModelInfo): String {
       return generatePropValidation(prop, model)
    }

    private fun generateSchema(model: ModelInfo): String {
        return model.properties.joinToString(",\n") { prop ->
            """
            "${prop.name}" to mapOf(
                "type" to "${getTypeName(prop.type)}",
                "required" to ${!prop.isNullable},
                "nullable" to ${prop.isNullable}",
                "constraints" to mapOf<String, Any>(
                    ${generatePropertySchema(prop)}
                )
            )
            """.trimIndent()
        }
    }

    private fun generatePropertySchema(prop: PropertyInfo): String {
        val schemaEntries = mutableListOf<String>()

        prop.annotations.forEach { (_, params) ->
            params.forEach { (key, value) ->
                val formattedValue = when (value) {
                    is String -> "\"$value\""
                    is List<*> -> "listOf(${value.joinToString(", ") { if (it is String) "\"$it\"" else it.toString() }})"
                    else -> value.toString()
                }
                schemaEntries.add("\"$key\" to $formattedValue")
            }
        }

        return schemaEntries.joinToString(",\n")
    }

    private fun getTypeName(type: String): String {
        val t = type.replace("?", "").trim()
        return when {
            t == "String" -> "string"
            t in listOf("Int", "Long", "Short", "Byte", "BigInteger") -> "integer"
            t in listOf("Float", "Double", "BigDecimal") -> "number"
            t == "Boolean" -> "boolean"
            t in listOf("LocalDate", "LocalDateTime", "LocalTime", "Instant", "ZonedDateTime") -> "datetime"
            t.startsWith("List") || t.startsWith("Set") || t.contains("Array") -> "array"
            t.startsWith("Map") -> "map"
            else -> "object"
        }
    }
}
