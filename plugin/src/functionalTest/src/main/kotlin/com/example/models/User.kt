package com.example.models

import com.pydantic.annotation.Serializable
import com.pydantic.annotation.Field

// Test annotation-based approach
@Serializable(strict = true)
data class User(
    @Field(minLength = 2, maxLength = 100)
    val name: String,

    @Field(min = 0, max = 150)
    val age: Int,

    @Field(minLength = 5, maxLength = 255, pattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\$")
    val email: String
)