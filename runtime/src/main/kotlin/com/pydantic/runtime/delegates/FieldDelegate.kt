package com.pydantic.runtime.delegates

import kotlin.reflect.KProperty
import com.pydantic.runtime.validation.ValidationError
import com.pydantic.runtime.validation.ValidationException
import com.pydantic.runtime.validation.constraints.*

class FieldDelegate<T : Any?>(
    private var initialValue: T? = null,
    private val validators: List<(T?) -> ValidationError?> = emptyList()
) {
    private var value: T? = initialValue

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value as T
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        // Run all validators
        val errors = validators.mapNotNull { validator ->
            validator(value)?.copy(field = property.name)
        }

        if (errors.isNotEmpty()) {
            throw ValidationException(errors)
        }

        this.value = value
    }
}

// DSL for creating field delegates
object Field {
    fun string(
        initialValue: String? = null,
        minLength: Int? = null,
        maxLength: Int? = null,
        pattern: String? = null,
        email: Boolean = false,
        url: Boolean = false,
        notBlank: Boolean = false,
        notEmpty: Boolean = false
    ): FieldDelegate<String> {
        val validators = mutableListOf<(String?) -> ValidationError?>()

        minLength?.let { min ->
            validators.add { value -> StringConstraints.minLength(value, min) }
        }

        maxLength?.let { max ->
            validators.add { value -> StringConstraints.maxLength(value, max) }
        }

        pattern?.let { regex ->
            validators.add { value -> StringConstraints.pattern(value, regex) }
        }

        if (email) {
            validators.add { value -> StringConstraints.email(value) }
        }

        if (url) {
            validators.add { value -> StringConstraints.url(value) }
        }

        if (notBlank) {
            validators.add { value -> StringConstraints.notBlank(value) }
        }

        if (notEmpty) {
            validators.add { value -> StringConstraints.notEmpty(value) }
        }

        return FieldDelegate(initialValue, validators)
    }

    fun int(
        initialValue: Int? = null,
        min: Int? = null,
        max: Int? = null,
        range: IntRange? = null
    ): FieldDelegate<Int> {
        val validators = mutableListOf<(Int?) -> ValidationError?>()

        min?.let { minVal ->
            validators.add { value -> NumberConstraints.min(value, minVal) }
        }

        max?.let { maxVal ->
            validators.add { value -> NumberConstraints.max(value, maxVal) }
        }

        range?.let { r ->
            validators.add { value -> NumberConstraints.range(value, r.first, r.last) }
        }

        return FieldDelegate(initialValue, validators)
    }

    fun long(
        initialValue: Long? = null,
        min: Long? = null,
        max: Long? = null
    ): FieldDelegate<Long> {
        val validators = mutableListOf<(Long?) -> ValidationError?>()

        min?.let { minVal ->
            validators.add { value -> NumberConstraints.min(value, minVal) }
        }

        max?.let { maxVal ->
            validators.add { value -> NumberConstraints.max(value, maxVal) }
        }

        return FieldDelegate(initialValue, validators)
    }

    fun double(
        initialValue: Double? = null,
        min: Double? = null,
        max: Double? = null
    ): FieldDelegate<Double> {
        val validators = mutableListOf<(Double?) -> ValidationError?>()

        min?.let { minVal ->
            validators.add { value -> NumberConstraints.min(value, minVal) }
        }

        max?.let { maxVal ->
            validators.add { value -> NumberConstraints.max(value, maxVal) }
        }

        return FieldDelegate(initialValue, validators)
    }

    fun <T : Any> custom(
        initialValue: T? = null,
        validator: (T?) -> ValidationError?
    ): FieldDelegate<T> {
        return FieldDelegate(initialValue, listOf(validator))
    }
}