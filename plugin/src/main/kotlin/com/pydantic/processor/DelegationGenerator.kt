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
        return model.properties.joinToString("\n") { prop ->
            """
            // Validation for ${prop.name}
            if (this.${prop.name} == null && ${!prop.isNullable}) {
                throw IllegalArgumentException("${prop.name} is required")
            }
            """.trimIndent()
        }
    }

    private fun generatePropertyExtensions(model: ModelInfo): String {
        return model.properties.joinToString("\n") { prop ->
            """
            val ${model.name}.${prop.name}Validator: (${prop.type}?) -> Boolean
                get() = { value ->
                    // Generated validation logic for ${prop.name}
                    ${generateSinglePropertyValidation(prop)}
                    true
                }
            """.trimIndent()
        }
    }

    private fun generateSinglePropertyValidation(prop: PropertyInfo): String {
        val validations = mutableListOf<String>()

        prop.annotations["Field"]?.let { fieldAnn ->
            fieldAnn["minLength"]?.let { minLength ->
                validations.add("""
                    if (value != null && value.length < $minLength) {
                        throw IllegalArgumentException("${prop.name} must be at least $minLength characters")
                    }
                """.trimIndent())
            }
            // ... other validations
        }

        return validations.joinToString("\n")
    }
}