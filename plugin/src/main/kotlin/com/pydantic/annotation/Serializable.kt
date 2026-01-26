package com.pydantic.annotation

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Serializable(
    val strict: Boolean = false,
    val alias: String = "",
    val validateOnCreate: Boolean = true
)