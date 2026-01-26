package com.pydantic.processor

import com.pydantic.runtime.validation.ModelInfo
import com.pydantic.runtime.validation.PropertyInfo

class ValidatorGenerator {

    fun generateValidator(model: ModelInfo, strict: Boolean): String {
        return """
            package ${model.packageName}
            
            import com.pydantic.runtime.validation.*
            import com.pydantic.runtime.validation.constraints.*
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
        val validations = mutableListOf<String>()
        val type = prop.type.lowercase()

        // String constraints
        if (type.contains("string")) {
            prop.annotations["Field"]?.let { fieldAnn ->
                fieldAnn["minLength"]?.let { minLength ->
                    validations.add("""
                    addValidation(${model.name}::${prop.name}) { value ->
                        StringConstraints.minLength(value, $minLength)?.copy(field = "${prop.name}")
                    }
                """.trimIndent())
                }
                fieldAnn["maxLength"]?.let { maxLength ->
                    validations.add("""
                    addValidation(${model.name}::${prop.name}) { value ->
                        StringConstraints.maxLength(value, $maxLength)?.copy(field = "${prop.name}")
                    }
                """.trimIndent())
                }
                fieldAnn["pattern"]?.let { pattern ->
                    validations.add("""
                    addValidation(${model.name}::${prop.name}) { value ->
                        StringConstraints.pattern(value, $pattern)?.copy(field = "${prop.name}")
                    }
                """.trimIndent())
                }
                fieldAnn["email"]?.let { email ->
                    validations.add("""
                    addValidation(${model.name}::${prop.name}) { value ->
                        StringConstraints.email(value, $email)?.copy(field = "${prop.name}")
                    }
                """.trimIndent())
                }
                fieldAnn["url"]?.let { url ->
                    validations.add("""
                    addValidation(${model.name}::${prop.name}) { value ->
                        StringConstraints.url(value, $url)?.copy(field = "${prop.name}")
                    }
                """.trimIndent())
                }
                fieldAnn["uuid"]?.let { uuid ->
                    validations.add("""
                    addValidation(${model.name}::${prop.name}) { value ->
                        StringConstraints.uuid(value, $uuid)?.copy(field = "${prop.name}")
                    }
                """.trimIndent())
                }
                fieldAnn["notBlank"]?.let { nblank ->
                    validations.add("""
                    addValidation(${model.name}::${prop.name}) { value ->
                        StringConstraints.notBlank(value, $nblank)?.copy(field = "${prop.name}")
                    }
                """.trimIndent())
                }
                fieldAnn["notEmpty"]?.let { nempty ->
                    validations.add("""
                    addValidation(${model.name}::${prop.name}) { value ->
                        StringConstraints.notEmpty(value, $nempty)?.copy(field = "${prop.name}")
                    }
                """.trimIndent())
                }
                fieldAnn["startsWith"]?.let { startsWith ->
                    validations.add("""
                    addValidation(${model.name}::${prop.name}) { value ->
                        StringConstraints.startsWith(value, $startsWith)?.copy(field = "${prop.name}")
                    }
                """.trimIndent())
                }
            }
        }
        // Required validation
        if (!prop.isNullable) {
            validations.add("""
                addValidation(${model.name}::${prop.name}) { value ->
                    if (value == null) ValidationError(
                        "${prop.name}",
                        "${prop.name} is required",
                        "REQUIRED",
                        null
                    ) else null
                }
            """.trimIndent())
        }

        // Field annotation validations using constraint objects
        prop.annotations["Field"]?.let { fieldAnn ->
            fieldAnn["minLength"]?.let { minLength ->
                validations.add("""
                    addValidation(${model.name}::${prop.name}) { value ->
                        StringConstraints.minLength(value, $minLength)?.copy(field = "${prop.name}")
                    }
                """.trimIndent())
            }

            fieldAnn["maxLength"]?.let { maxLength ->
                validations.add("""
                    addValidation(${model.name}::${prop.name}) { value ->
                        StringConstraints.maxLength(value, $maxLength)?.copy(field = "${prop.name}")
                    }
                """.trimIndent())
            }

            fieldAnn["pattern"]?.let { pattern ->
                validations.add("""
                    addValidation(${model.name}::${prop.name}) { value ->
                        StringConstraints.pattern(value, "$pattern")?.copy(field = "${prop.name}")
                    }
                """.trimIndent())
            }

            fieldAnn["min"]?.let { min ->
                validations.add("""
                    addValidation(${model.name}::${prop.name}) { value ->
                        NumberConstraints.min(value, $min)?.copy(field = "${prop.name}")
                    }
                """.trimIndent())
            }

            fieldAnn["max"]?.let { max ->
                validations.add("""
                    addValidation(${model.name}::${prop.name}) { value ->
                        NumberConstraints.max(value, $max)?.copy(field = "${prop.name}")
                    }
                """.trimIndent())
            }
        }

        // Special annotation validations
        prop.annotations["Email"]?.let {
            validations.add("""
                addValidation(${model.name}::${prop.name}) { value ->
                    StringConstraints.email(value)?.copy(field = "${prop.name}")
                }
            """.trimIndent())
        }

        prop.annotations["Url"]?.let {
            validations.add("""
                addValidation(${model.name}::${prop.name}) { value ->
                    StringConstraints.url(value)?.copy(field = "${prop.name}")
                }
            """.trimIndent())
        }

        prop.annotations["Pattern"]?.let { patternAnn ->
            patternAnn["regex"]?.let { regex ->
                validations.add("""
                    addValidation(${model.name}::${prop.name}) { value ->
                        StringConstraints.pattern(value, "$regex")?.copy(field = "${prop.name}")
                    }
                """.trimIndent())
            }
        }

        return validations.joinToString("\n")
    }

    private fun generateSchema(model: ModelInfo): String {
        return model.properties.joinToString(",\n") { prop ->
            """
            "${prop.name}" to mapOf(
                "type" to "${getTypeName(prop.type)}",
                "required" to ${!prop.isNullable},
                ${generatePropertySchema(prop)}
            )
            """.trimIndent()
        }
    }

    private fun generatePropertySchema(prop: PropertyInfo): String {
        val constraints = mutableListOf<String>()

        prop.annotations["Field"]?.forEach { (key, value) ->
            when (key) {
                "min", "max", "minLength", "maxLength" -> {
                    constraints.add("\"$key\" to $value")
                }
                "pattern" -> {
                    constraints.add("\"pattern\" to \"$value\"")
                }
                "description" -> {
                    constraints.add("\"description\" to \"$value\"")
                }
                "example" -> {
                    constraints.add("\"example\" to \"$value\"")
                }
            }
        }

        return constraints.joinToString(",\n")
    }

    private fun getTypeName(type: String): String {
        return when {
            type.contains("String") -> "string"
            type.contains("Int") || type.contains("Long") -> "integer"
            type.contains("Float") || type.contains("Double") -> "number"
            type.contains("Boolean") -> "boolean"
            type.contains("List") || type.contains("Array") -> "array"
            else -> "object"
        }
    }
}
