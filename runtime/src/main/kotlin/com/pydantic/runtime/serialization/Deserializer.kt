package com.pydantic.runtime.serialization

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.deser.ContextualDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode
import java.lang.reflect.Type
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor

class PydanticDeserializer<T : Any>(
    private val targetClass: KClass<T>? = null
) : JsonDeserializer<T>(), ContextualDeserializer {

    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): T {
        val node = parser.codec.readTree<JsonNode>(parser)
        val clazz = targetClass ?: throw JsonMappingException(parser, "Target class not resolved")

        return createInstance(node, clazz, ctxt)
    }

    private fun createInstance(node: JsonNode, clazz: KClass<T>, ctxt: DeserializationContext): T {
        // 1. Find the primary constructor (Pydantic style relies on the constructor for validation)
        val constructor = clazz.primaryConstructor
            ?: throw JsonMappingException(ctxt.parser, "Class ${clazz.simpleName} must have a primary constructor")

        // 2. Map JSON fields to constructor parameters
        val args = mutableMapOf<KParameter, Any?>()

        for (param in constructor.parameters) {
            val name = param.name ?: continue
            val jsonValue = node.get(name)

            if (jsonValue == null || jsonValue.isNull) {
                // If value is missing and not optional, it will fail during callBy()
                // unless we handle defaults here.
                if (!param.isOptional && !param.type.isMarkedNullable) {
                    throw JsonMappingException(ctxt.parser, "Missing required field: $name")
                }
                continue
            }

            // 3. Convert JsonNode to the required parameter type using Jackson's internal machinery
            val paramJavaType = ctxt.typeFactory.constructType(param.type.classifier as? Type)
            val value = ctxt.readTreeAsValue<Any>(jsonValue, paramJavaType)

            args[param] = value
        }

        // 4. Instantiate the class using the map of arguments
        // callBy is used instead of call so that default parameter values are respected
        return try {
            constructor.callBy(args)
        } catch (e: Exception) {
            throw JsonMappingException(ctxt.parser, "Failed to instantiate ${clazz.simpleName}: ${e.message}", e)
        }
    }

    /**
     * This method is called by Jackson to find out the specific type being deserialized.
     * It allows us to know that we are deserializing 'User' vs 'Order'.
     */
    override fun createContextual(ctxt: DeserializationContext, property: BeanProperty?): JsonDeserializer<*> {
        val type = ctxt.contextualType ?: property?.type
        @Suppress("UNCHECKED_CAST")
        val rawClass = type?.rawClass?.kotlin as? KClass<T>
        return PydanticDeserializer(rawClass)
    }
}