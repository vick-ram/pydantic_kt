package com.pydantic.processor

import com.pydantic.runtime.validation.ModelInfo
import com.pydantic.runtime.validation.PropertyInfo

class DelegationGenerator {

    fun generateDelegates(model: ModelInfo): String {
        return """
            package ${model.packageName}
            
            import com.pydantic.runtime.delegates.*
            import kotlin.reflect.KProperty
            
            // Extension functions for ${model.name}
            fun ${model.name}.validateDelegates(): Boolean {
                ${generateValidationChecks(model)}
                return true
            }
            
            ${generatePropertyExtensions(model)}
        """.trimIndent()
    }

    private fun generateValidationChecks(model: ModelInfo): String {
        return """
        fun ${model.name}.validate(): List<ValidationError> {
            val errors = mutableListOf<ValidationError>()
            
            ${model.properties.joinToString("\n") { prop ->
            val nullCheck = if (!prop.isNullable) {
                """
                    if (this.${prop.name} == null) {
                        errors.add(ValidationError(
                            field = "${prop.name}",
                            message = "${prop.name} is required",
                            code = "REQUIRED"
                        ))
                    }
                    """.trimIndent()
            } else ""

            nullCheck
        }}
            
            // Here you would also trigger the other constraints
            return errors
        }
    """.trimIndent()
    }

    private fun generatePropertyExtensions(model: ModelInfo): String {
        return model.properties.joinToString("\n") { prop ->
            """
            val ${model.name}.${prop.name}Validator: (${prop.type}?) -> Boolean
                get() = { value ->
                    // Generated validation logic for ${prop.name}
                    ${generateSinglePropertyValidation(prop, model)}
                    true
                }
            """.trimIndent()
        }
    }

    private fun generateSinglePropertyValidation(prop: PropertyInfo, model: ModelInfo): String {
        return generatePropValidation(prop, model)
    }
}