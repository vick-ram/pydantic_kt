package com.pydantic.runtime.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.*
import kotlin.reflect.full.memberProperties

class PydanticSerializer<T : Any> : JsonSerializer<T>() {

    override fun serialize(value: T, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()

        value::class.memberProperties.forEach { property ->
            val propertyValue = property.getter.call(value)
            gen.writeFieldName(property.name)
            serializers.defaultSerializeValue(propertyValue, gen)
        }

        gen.writeEndObject()
    }

    override fun handledType(): Class<T> {
        @Suppress("UNCHECKED_CAST")
        return Any::class.java as Class<T>
    }
}