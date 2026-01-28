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
        @Suppress("UNCHECKED_CAST")
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
        lowercase: Boolean = false,
        uppercase: Boolean = false,
        slug: Boolean = false,
        pattern: String? = null,
        email: Boolean = false,
        startsWith: String? = null,
        endsWith: String? = null,
        contains: String? = null,
        equals: String? = null,
        oneOf: Set<String>? = null,
        url: Boolean = false,
        uuid: Boolean = false,
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

        if (lowercase) {
            validators.add { value -> StringConstraints.lowercase(value) }
        }

        if (uppercase) {
            validators.add { value -> StringConstraints.uppercase(value) }
        }

        if (uppercase) {
            validators.add { value -> StringConstraints.uppercase(value) }
        }

        if (slug) {
            validators.add { value -> StringConstraints.slug(value) }
        }

        pattern?.let { regex ->
            validators.add { value -> StringConstraints.pattern(value, regex) }
        }

        startsWith?.let { prefix ->
            validators.add { value -> StringConstraints.startsWith(value, prefix) }
        }

        endsWith?.let { suffix ->
            validators.add { value -> StringConstraints.endsWith(value, suffix) }
        }

        contains?.let { suffix ->
            validators.add { value -> StringConstraints.contains(value, suffix) }
        }

        equals?.let { other ->
            validators.add { value -> StringConstraints.equalsIgnoreCase(value, other) }
        }

        oneOf?.let { allowed ->
            validators.add { value -> StringConstraints.oneOf(value, allowed) }
        }

        if (email) {
            validators.add { value -> StringConstraints.email(value) }
        }

        if (url) {
            validators.add { value -> StringConstraints.url(value) }
        }

        if (uuid) {
            validators.add { value -> StringConstraints.uuid(value) }
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
        equals: Int? = null,
        notEqual: Int? = null,
        oneOf: Set<Int>? = null,
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

        equals?.let { eq ->
            validators.add { value -> NumberConstraints.equals(value, eq) }
        }

        notEqual?.let { notEq ->
            validators.add { value -> NumberConstraints.notEquals(value, notEq) }
        }

        oneOf?.let { allowed ->
            validators.add { value -> NumberConstraints.oneOf(value, allowed) }
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