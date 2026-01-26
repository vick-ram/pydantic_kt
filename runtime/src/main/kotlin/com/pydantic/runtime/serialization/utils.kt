package com.pydantic.runtime.serialization

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.module.SimpleModule
import com.pydantic.runtime.validation.ValidationError
import com.pydantic.runtime.validation.ValidationException
import kotlin.reflect.full.memberProperties

object SerializationUtils {

    fun createObjectMapper(): ObjectMapper {
        return ObjectMapper().apply {
            registerModule(createPydanticModule())
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(SerializationFeature.INDENT_OUTPUT, true)
        }
    }

    private fun createPydanticModule(): SimpleModule {
        return SimpleModule().apply {
            addSerializer(PydanticSerializer<Any>())
            addDeserializer(Any::class.java,
                PydanticDeserializer()
            )
        }
    }

    inline fun <reified T : Any> fromJson(json: String): T {
        val mapper = createObjectMapper()
        return try {
            mapper.readValue(json, T::class.java)
        } catch (e: Exception) {
            throw ValidationException(
                listOf(
                    ValidationError(
                        field = "json",
                        message = "Failed to deserialize JSON: ${e.message}",
                        code = "DESERIALIZATION_ERROR",
                        value = json
                    )
                )
            )
        }
    }

    fun <T : Any> toJson(value: T): String {
        val mapper = createObjectMapper()
        return mapper.writeValueAsString(value)
    }

    fun <T : Any> toMap(value: T): Map<String, Any?> {
        return value::class.memberProperties.associate { property ->
            property.name to property.getter.call(value)
        }
    }

    inline fun <reified T : Any> fromMap(map: Map<String, Any?>): T {
        val json = createObjectMapper().writeValueAsString(map)
        return fromJson(json)
    }
}