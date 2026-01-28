package com.pydantic.runtime.delegates

import com.pydantic.runtime.validation.ValidationError
import com.pydantic.runtime.validation.constraints.DateTimeConstraints
import com.pydantic.runtime.validation.constraints.NumberConstraints
import com.pydantic.runtime.validation.constraints.StringConstraints
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.Temporal

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

// ---------- STRING ----------
fun FieldBuilder<String>.minLength(min: Int) = validator { value ->
    StringConstraints.minLength(value, min)
}

fun FieldBuilder<String>.maxLength(max: Int) = validator { value ->
    StringConstraints.maxLength(value, max)
}

fun FieldBuilder<String>.lowercase() = validator { value ->
    StringConstraints.lowercase(value)
}

fun FieldBuilder<String>.uppercase() = validator { value ->
    StringConstraints.uppercase(value)
}

fun FieldBuilder<String>.slug() = validator { value ->
    StringConstraints.slug(value)
}

fun FieldBuilder<String>.ascii() = validator { value ->
    StringConstraints.ascii(value)
}

fun FieldBuilder<String>.trimmed() = validator { value ->
    StringConstraints.trimmed(value)
}

fun FieldBuilder<String>.pattern(regex: String) = validator { value ->
    StringConstraints.pattern(value, regex)
}

fun FieldBuilder<String>.email() = validator { value ->
    StringConstraints.email(value)
}

fun FieldBuilder<String>.url() = validator { value ->
    StringConstraints.url(value)
}

fun FieldBuilder<String>.uuid() = validator { value ->
    StringConstraints.uuid(value)
}

fun FieldBuilder<String>.notBlank() = validator { value ->
    StringConstraints.notBlank(value)
}

fun FieldBuilder<String>.notEmpty() = validator { value ->
    StringConstraints.notEmpty(value)
}

fun FieldBuilder<String>.startsWith(prefix: String) = validator { value ->
    StringConstraints.startsWith(value, prefix)
}

fun FieldBuilder<String>.endsWith(suffix: String) = validator { value ->
    StringConstraints.endsWith(value, suffix)
}

fun FieldBuilder<String>.contains(substring: String) = validator { value ->
    StringConstraints.contains(value, substring)
}

fun FieldBuilder<String>.equalsTo(other: String) = validator { value ->
    StringConstraints.equalsIgnoreCase(value, other)
}

// ------------- NUMBER ------------
fun <T> FieldBuilder<T>.min(minValue: T)
        where T : Number, T : Comparable<T> = validator { value ->
    NumberConstraints.min(value, minValue)
}

fun <T> FieldBuilder<T>.max(maxValue: T)
        where T : Number, T : Comparable<T> = validator { value ->
    NumberConstraints.max(value, maxValue)
}

fun <T> FieldBuilder<T>.range(minValue: T, maxValue: T)
        where T : Number, T : Comparable<T> = validator { value ->
    NumberConstraints.range(value, minValue, maxValue)
}

fun <T> FieldBuilder<T>.greaterThan(other: T)
        where T : Number, T : Comparable<T> = validator { value ->
    NumberConstraints.greaterThan(value, other)
}

fun <T> FieldBuilder<T>.lessThan(other: T)
        where T : Number, T : Comparable<T> = validator { value ->
    NumberConstraints.lessThan(value, other)
}

fun <T : Number> FieldBuilder<T>.equalsTo(other: T) = validator { value ->
    NumberConstraints.equals(value, other)
}

fun <T : Number> FieldBuilder<T>.divisibleBy(divisor: T) = validator { value ->
    NumberConstraints.divisibleBy(value, divisor)
}

// ------------ DATE & TIME -----------
fun FieldBuilder<String>.dateTime(format: String? = null) = validator { value ->
    DateTimeConstraints.isDateTime(value, format)
}

fun FieldBuilder<String>.date(format: String? = null) = validator { value ->
    DateTimeConstraints.isDate(value, format)
}

fun FieldBuilder<String>.time(format: String? = null) = validator { value ->
    DateTimeConstraints.isTime(value, format)
}

fun <T> FieldBuilder<T>.before(reference: T)
        where T : Temporal, T : Comparable<T> = validator { value ->
    DateTimeConstraints.before(value, reference)
}

fun <T> FieldBuilder<T>.after(reference: T)
        where T : Temporal, T : Comparable<T> = validator { value ->
    DateTimeConstraints.after(value, reference)
}

fun <T> FieldBuilder<T>.between(startValue: T, endValue: T) where T : Temporal, T : Comparable<T> = validator { value ->
    DateTimeConstraints.between(value, startValue, endValue)
}

fun <T : Temporal> FieldBuilder<T>.past(amount: Long, unit: ChronoUnit) = validator { value ->
    DateTimeConstraints.withinPast(value, amount, unit)
}

fun <T : Temporal> FieldBuilder<T>.future(amount: Long, unit: ChronoUnit) = validator { value ->
    DateTimeConstraints.withinFuture(value, amount, unit)
}

fun <T : Temporal> FieldBuilder<T>.weekday() = validator { value ->
    DateTimeConstraints.isWeekday(value)
}

fun <T : Temporal> FieldBuilder<T>.weekend() = validator { value ->
    DateTimeConstraints.isWeekend(value)
}

fun <T: Temporal> FieldBuilder<T>.timeBetween(start: LocalTime, end: LocalTime) = validator { value ->
    DateTimeConstraints.timeBetween(value, start, end)
}

fun FieldBuilder<ZonedDateTime>.inTimeZone(zoneId: ZoneId) = validator { value ->
    DateTimeConstraints.inTimeZone(value, zoneId)
}

// Specialized functions
fun stringField(
    block: FieldBuilder<String>.() -> Unit
): FieldDelegate<String> = FieldBuilder<String>().apply(block).build()

fun intField(
    block: FieldBuilder<Int>.() -> Unit
): FieldDelegate<Int> = FieldBuilder<Int>().apply(block).build()

fun longField(
    block: FieldBuilder<Long>.() -> Unit
): FieldDelegate<Long> = FieldBuilder<Long>().apply(block).build()

fun floatField(
    block: FieldBuilder<Float>.() -> Unit
): FieldDelegate<Float> = FieldBuilder<Float>().apply(block).build()

fun doubleField(
    block: FieldBuilder<Double>.() -> Unit
): FieldDelegate<Double> =
    FieldBuilder<Double>().apply(block).build()

fun booleanField(
    block: FieldBuilder<Boolean>.() -> Unit
): FieldDelegate<Boolean> = FieldBuilder<Boolean>().apply(block).build()

fun dateField(block: FieldBuilder<LocalDate>.() -> Unit): FieldDelegate<LocalDate> =
    FieldBuilder<LocalDate>().apply(block).build()

fun timeField(
    block: FieldBuilder<LocalTime>.() -> Unit
): FieldDelegate<LocalTime> = FieldBuilder<LocalTime>().apply(block).build()

fun dateTimeField(
    block: FieldBuilder<LocalDateTime>.() -> Unit
): FieldDelegate<LocalDateTime> = FieldBuilder<LocalDateTime>().apply(block).build()

fun zonedDateTimeField(
    block: FieldBuilder<ZonedDateTime>.() -> Unit
): FieldDelegate<ZonedDateTime> = FieldBuilder<ZonedDateTime>().apply(block).build()

fun instantField(
    block: FieldBuilder<Instant>.() -> Unit
): FieldDelegate<Instant> =
    FieldBuilder<Instant>().apply(block).build()

fun bigDecimalField(
    block: FieldBuilder<BigDecimal>.() -> Unit
): FieldDelegate<BigDecimal> = FieldBuilder<BigDecimal>().apply(block).build()

fun bigIntegerField(
    block: FieldBuilder<BigInteger>.() -> Unit
): FieldDelegate<BigInteger> = FieldBuilder<BigInteger>().apply(block).build()

fun <T : Any> listField(
    block: FieldBuilder<List<T>>.() -> Unit
): FieldDelegate<List<T>> = FieldBuilder<List<T>>().apply(block).build()

fun <T : Any> setField(
    block: FieldBuilder<Set<T>>.() -> Unit
): FieldDelegate<Set<T>> = FieldBuilder<Set<T>>().apply(block).build()

fun <K : Any, V : Any> mapField(
    block: FieldBuilder<Map<K, V>>.() -> Unit
): FieldDelegate<Map<K, V>> = FieldBuilder<Map<K, V>>().apply(block).build()

fun <E : Enum<E>> enumField(
    block: FieldBuilder<E>.() -> Unit
): FieldDelegate<E> = FieldBuilder<E>().apply(block).build()

inline fun <reified T : Any> field(block: FieldBuilder<T>.() -> Unit): FieldDelegate<T> {
    return FieldBuilder<T>().apply(block).build()
}

