package com.pydantic.annotation

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
annotation class Field(
    val min: Long = Long.MIN_VALUE,
    val max: Long = Long.MAX_VALUE,
    val minLength: Int = 0,
    val maxLength: Int = Int.MAX_VALUE,
    val pattern: String = "",
    val required: Boolean = true,
    val defaultValue: String = "",
    val alias: String = "",
    val description: String = "",
    val example: String = "",
    val deprecated: Boolean = false
)