package com.pydantic.runtime.delegates

import com.pydantic.runtime.validation.ValidationError
import com.pydantic.runtime.validation.constraints.NumberConstraints
import com.pydantic.runtime.validation.constraints.StringConstraints

class FieldBuilder<T : Any> {
    var initialValue: T? = null
    private val validators = mutableListOf<(T?) -> ValidationError?>()

    fun initial(value: T) = apply { this.initialValue = value }

    fun validator(validator: (T?) -> ValidationError?) = apply {
        validators.add(validator)
    }

    fun build(): FieldDelegate<T> {
        return FieldDelegate(initialValue, validators)
    }
}

fun FieldBuilder<String>.minLength(min: Int) = validator { value ->
    StringConstraints.minLength(value, min)
}

fun FieldBuilder<String>.maxLength(max: Int) = validator { value ->
    StringConstraints.maxLength(value, max)
}

fun FieldBuilder<String>.pattern(regex: String) = validator { value ->
    StringConstraints.pattern(value, regex)
}

fun FieldBuilder<String>.email() = validator { value ->
    StringConstraints.email(value)
}

fun <T> FieldBuilder<T>.min(minValue: T)
        where T : Number, T : Comparable<T> = validator { value ->
    NumberConstraints.min(value, minValue)
}

fun <T> FieldBuilder<T>.max(maxValue: T)
        where T : Number, T : Comparable<T> = validator { value ->
    NumberConstraints.max(value, maxValue)
}

inline fun <reified T : Any> field(block: FieldBuilder<T>.() -> Unit): FieldDelegate<T> {
    return FieldBuilder<T>().apply(block).build()
}

// Specialized functions
fun stringField(
    block: FieldBuilder<String>.() -> Unit
): FieldDelegate<String> =
    FieldBuilder<String>().apply(block).build()

fun intField(
    block: FieldBuilder<Int>.() -> Unit
): FieldDelegate<Int> =
    FieldBuilder<Int>().apply(block).build()

fun longField(
    block: FieldBuilder<Long>.() -> Unit
): FieldDelegate<Long> =
    FieldBuilder<Long>().apply(block).build()

fun floatField(
    block: FieldBuilder<Float>.() -> Unit
): FieldDelegate<Float> =
    FieldBuilder<Float>().apply(block).build()

fun doubleField(
    block: FieldBuilder<Double>.() -> Unit
): FieldDelegate<Double> =
    FieldBuilder<Double>().apply(block).build()

fun booleanField(
    block: FieldBuilder<Boolean>.() -> Unit
): FieldDelegate<Boolean> =
    FieldBuilder<Boolean>().apply(block).build()
