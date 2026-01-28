package com.pydantic.runtime.validation

import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

interface Validator<T : Any> {
    fun validate(value: T): ValidationResult
    fun validatePartial(value: T): ValidationResult
    fun getSchema(): Map<String, Any>
}

abstract class BaseValidator<T: Any>(private val modelClas: KClass<T>) : Validator<T> {
    protected val validations = mutableListOf<(T) -> List<ValidationError>>()

    override fun validate(value: T): ValidationResult {
        val errors = validations.flatMap { it(value) }
        return ValidationResult(
            modelName = modelClas.simpleName ?: "Unknown",
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = emptyList()
        )
    }

    override fun validatePartial(value: T): ValidationResult {
        val errors = validations.flatMap { it(value) }
            .filterNot { it.code == "REQUIRED" }
        return ValidationResult(
            modelName = modelClas.simpleName ?: "Unknown",
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = errors
        )
    }

    protected fun <V> addValidation(property: KProperty1<T, V>, validator: (V) -> ValidationError?) {
        validations.add {obj ->
            val value = property.get(obj)
            listOfNotNull(validator(value))
        }
    }

    override fun getSchema(): Map<String, Any> = emptyMap()
}