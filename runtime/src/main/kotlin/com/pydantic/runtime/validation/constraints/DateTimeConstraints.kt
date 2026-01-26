package com.pydantic.runtime.validation.constraints

import com.pydantic.runtime.validation.ValidationError
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.time.temporal.Temporal

object DateTimeConstraints {

    fun isDateTime(value: String?, format: String? = null): ValidationError? {
        return if (value != null) {
            try {
                if (format != null) {
                    LocalDateTime.parse(value, DateTimeFormatter.ofPattern(format))
                } else {
                    // Try common formats
                    value.let { input ->
                        listOf<(String) -> Any>(
                            { LocalDateTime.parse(it) },
                            { LocalDate.parse(it).atStartOfDay() },
                            { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDateTime() }
                        ).firstOrNull { parser ->
                            try {
                                parser(input)
                                true
                            } catch (e: DateTimeParseException) {
                                false
                            }
                        }
                    }

                }
                null
            } catch (e: Exception) {
                ValidationError(
                    field = "",
                    message = "Invalid date-time format${if (format != null) " (expected: $format)" else ""}",
                    code = "DATETIME_INVALID",
                    value = value,
                    constraints = mapOf("format" to (format ?: "ISO-8601"))
                )
            }
        } else null
    }

    fun isDate(value: String?, format: String? = null): ValidationError? {
        return if (value != null) {
            try {
                if (format != null) {
                    LocalDate.parse(value, DateTimeFormatter.ofPattern(format))
                } else {
                    LocalDate.parse(value)
                }
                null
            } catch (e: DateTimeParseException) {
                ValidationError(
                    field = "",
                    message = "Invalid date format${if (format != null) " (expected: $format)" else ""}",
                    code = "DATE_INVALID",
                    value = value,
                    constraints = mapOf("format" to (format ?: "ISO-8601"))
                )
            }
        } else null
    }

    fun isTime(value: String?, format: String? = null): ValidationError? {
        return if (value != null) {
            try {
                if (format != null) {
                    LocalTime.parse(value, DateTimeFormatter.ofPattern(format))
                } else {
                    LocalTime.parse(value)
                }
                null
            } catch (e: DateTimeParseException) {
                ValidationError(
                    field = "",
                    message = "Invalid time format${if (format != null) " (expected: $format)" else ""}",
                    code = "TIME_INVALID",
                    value = value,
                    constraints = mapOf("format" to (format ?: "ISO-8601"))
                )
            }
        } else null
    }

    fun <T : Temporal> before(value: T?, reference: T): ValidationError? where T : Comparable<T> {
        return if (value != null && value >= reference) {
            ValidationError(
                field = "",
                message = "Value must be before $reference",
                code = "DATETIME_BEFORE",
                value = value,
                constraints = mapOf("reference" to reference.toString())
            )
        } else null
    }

    fun <T : Temporal> after(value: T?, reference: T): ValidationError? where T : Comparable<T> {
        return if (value != null && value <= reference) {
            ValidationError(
                field = "",
                message = "Value must be after $reference",
                code = "DATETIME_AFTER",
                value = value,
                constraints = mapOf("reference" to reference.toString())
            )
        } else null
    }

    fun <T : Temporal> between(value: T?, start: T, end: T): ValidationError? where T : Comparable<T> {
        return if (value != null && (value <= start || value >= end)) {
            ValidationError(
                field = "",
                message = "Value must be between $start and $end",
                code = "DATETIME_BETWEEN",
                value = value,
                constraints = mapOf("start" to start.toString(), "end" to end.toString())
            )
        } else null
    }

    fun <T : Temporal> withinPast(value: T?, amount: Long, unit: ChronoUnit): ValidationError? {
        return if (value != null) {
            val now = when (value) {
                is LocalDateTime -> LocalDateTime.now()
                is LocalDate -> LocalDate.now()
                is LocalTime -> LocalTime.now()
                is Instant -> Instant.now()
                is ZonedDateTime -> ZonedDateTime.now()
                else -> null
            }

            if (now != null) {
                val diff = when (unit) {
                    ChronoUnit.DAYS -> ChronoUnit.DAYS.between(value, now)
                    ChronoUnit.HOURS -> ChronoUnit.HOURS.between(value, now)
                    ChronoUnit.MINUTES -> ChronoUnit.MINUTES.between(value, now)
                    ChronoUnit.SECONDS -> ChronoUnit.SECONDS.between(value, now)
                    else -> 0
                }

                if (diff > amount) {
                    ValidationError(
                        field = "",
                        message = "Value must be within past $amount ${unit.name.lowercase()}",
                        code = "DATETIME_WITHIN_PAST",
                        value = value,
                        constraints = mapOf("amount" to amount, "unit" to unit.name)
                    )
                } else null
            } else null
        } else null
    }

    fun <T : Temporal> withinFuture(value: T?, amount: Long, unit: ChronoUnit): ValidationError? {
        return if (value != null) {
            val now = when (value) {
                is LocalDateTime -> LocalDateTime.now()
                is LocalDate -> LocalDate.now()
                is LocalTime -> LocalTime.now()
                is Instant -> Instant.now()
                is ZonedDateTime -> ZonedDateTime.now()
                else -> null
            }

            if (now != null) {
                val diff = when (unit) {
                    ChronoUnit.DAYS -> ChronoUnit.DAYS.between(now, value)
                    ChronoUnit.HOURS -> ChronoUnit.HOURS.between(now, value)
                    ChronoUnit.MINUTES -> ChronoUnit.MINUTES.between(now, value)
                    ChronoUnit.SECONDS -> ChronoUnit.SECONDS.between(now, value)
                    else -> 0
                }

                if (diff > amount) {
                    ValidationError(
                        field = "",
                        message = "Value must be within next $amount ${unit.name.lowercase()}",
                        code = "DATETIME_WITHIN_FUTURE",
                        value = value,
                        constraints = mapOf("amount" to amount, "unit" to unit.name)
                    )
                } else null
            } else null
        } else null
    }

    fun <T : Temporal> isWeekday(value: T?): ValidationError? {
        return if (value != null) {
            val dayOfWeek = when (value) {
                is LocalDateTime -> value.dayOfWeek
                is LocalDate -> value.dayOfWeek
                is ZonedDateTime -> value.dayOfWeek
                else -> null
            }

            if (dayOfWeek != null && (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY)) {
                ValidationError(
                    field = "",
                    message = "Date must be a weekday",
                    code = "DATETIME_WEEKDAY",
                    value = value,
                    constraints = mapOf("dayOfWeek" to dayOfWeek.name)
                )
            } else null
        } else null
    }

    fun <T : Temporal> isWeekend(value: T?): ValidationError? {
        return if (value != null) {
            val dayOfWeek = when (value) {
                is LocalDateTime -> value.dayOfWeek
                is LocalDate -> value.dayOfWeek
                is ZonedDateTime -> value.dayOfWeek
                else -> null
            }

            if (dayOfWeek != null && dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) {
                ValidationError(
                    field = "",
                    message = "Date must be a weekend",
                    code = "DATETIME_WEEKEND",
                    value = value,
                    constraints = mapOf("dayOfWeek" to dayOfWeek.name)
                )
            } else null
        } else null
    }

    fun <T : Temporal> timeBetween(value: T?, startTime: LocalTime, endTime: LocalTime): ValidationError? {
        return if (value != null) {
            val time = when (value) {
                is LocalDateTime -> value.toLocalTime()
                is LocalTime -> value
                is ZonedDateTime -> value.toLocalTime()
                else -> null
            }

            if (time != null && (time.isBefore(startTime) || time.isAfter(endTime))) {
                ValidationError(
                    field = "",
                    message = "Time must be between $startTime and $endTime",
                    code = "TIME_BETWEEN",
                    value = value,
                    constraints = mapOf("startTime" to startTime.toString(), "endTime" to endTime.toString())
                )
            } else null
        } else null
    }

    fun <T : Temporal> inTimeZone(value: T?, zoneId: ZoneId): ValidationError? {
        return if (value is ZonedDateTime && value.zone != zoneId) {
            ValidationError(
                field = "",
                message = "DateTime must be in timezone $zoneId",
                code = "DATETIME_TIMEZONE",
                value = value,
                constraints = mapOf("expectedZone" to zoneId.id, "actualZone" to value.zone.id)
            )
        } else null
    }
}