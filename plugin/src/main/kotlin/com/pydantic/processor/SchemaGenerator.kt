package com.pydantic.processor

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.lang.reflect.Modifier
import kotlin.collections.forEach

class SchemaGenerator {

    private val mapper: ObjectMapper = jacksonObjectMapper()

    fun generateSchema(className: String, packageName: String): Map<String, Any> {
        return try {
            val clazz = Class.forName("$packageName.$className")
            generateClassSchema(clazz)
        } catch (e: ClassNotFoundException) {
            generateStubSchema(className)
        }
    }

    private fun generateClassSchema(clazz: Class<*>): Map<String, Any> {
        val schema = mutableMapOf<String, Any>()

        schema["title"] = clazz.simpleName
        schema["type"] = "object"
        schema["description"] = clazz.annotations.find { it.annotationClass.simpleName == "Description" }
            ?.let { getAnnotationValue(it, "value") } ?: ""

        val properties = mutableMapOf<String, Any>()
        val required = mutableListOf<String>()

        clazz.declaredFields.forEach { field ->
            if (!Modifier.isStatic(field.modifiers)) {
                val fieldName = field.name
                val fieldType = field.type

                val fieldSchema = generateFieldSchema(field, fieldType)
                properties[fieldName] = fieldSchema

                // Check if field is required
                if (field.annotations.any { it.annotationClass.simpleName == "NotNull" } ||
                    field.type.isPrimitive) {
                    required.add(fieldName)
                }

                // Add field-specific annotations
                field.annotations.forEach { annotation ->
                    when (annotation.annotationClass.simpleName) {
                        "Min" -> {
                            (fieldSchema as MutableMap<String, Any>)["minimum"] =
                                getAnnotationValue(annotation, "value") as? Any as Any
                        }
                        "Max" -> {
                            (fieldSchema as MutableMap<String, Any>)["maximum"] =
                                getAnnotationValue(annotation, "value") as? Any as Any
                        }
                        "Size" -> {
                            (fieldSchema as MutableMap<String, Any>).apply {
                                getAnnotationValue<Int>(annotation, "min")?.let {
                                    put("minLength", it)
                                }
                                getAnnotationValue<Int>(annotation, "max")?.let {
                                    put("maxLength", it)
                                }
                            }
                        }
                        "Pattern" -> {
                            (fieldSchema as MutableMap<String, Any>)["pattern"] =
                                getAnnotationValue(annotation, "regexp") ?: ""
                        }
                        "Email" -> {
                            (fieldSchema as MutableMap<String, Any>)["format"] = "email"
                        }
                    }
                }
            }
        }

        schema["properties"] = properties
        if (required.isNotEmpty()) {
            schema["required"] = required
        }

        // Add examples if available
        val example = generateExample(clazz)
        if (example.isNotEmpty()) {
            schema["example"] = example
        }

        return schema
    }

    private fun generateFieldSchema(field: java.lang.reflect.Field, type: Class<*>): Map<String, Any> {
        val fieldSchema = mutableMapOf<String, Any>()

        when {
            type == String::class.java -> {
                fieldSchema["type"] = "string"
                fieldSchema["description"] = field.name
            }
            type == Int::class.java || type == Integer::class.java -> {
                fieldSchema["type"] = "integer"
                fieldSchema["description"] = field.name
            }
            type == Long::class.java || type == java.lang.Long::class.java -> {
                fieldSchema["type"] = "integer"
                fieldSchema["format"] = "int64"
                fieldSchema["description"] = field.name
            }
            type == Double::class.java || type == java.lang.Double::class.java -> {
                fieldSchema["type"] = "number"
                fieldSchema["format"] = "double"
                fieldSchema["description"] = field.name
            }
            type == Float::class.java || type == java.lang.Float::class.java -> {
                fieldSchema["type"] = "number"
                fieldSchema["format"] = "float"
                fieldSchema["description"] = field.name
            }
            type == Boolean::class.java || type == java.lang.Boolean::class.java -> {
                fieldSchema["type"] = "boolean"
                fieldSchema["description"] = field.name
            }
            type.isArray || type == List::class.java -> {
                fieldSchema["type"] = "array"
                fieldSchema["description"] = field.name
                // Try to determine item type
                val itemType = getCollectionItemType(field)
                fieldSchema["items"] = mapOf("type" to getTypeName(itemType))
            }
            type == Map::class.java -> {
                fieldSchema["type"] = "object"
                fieldSchema["additionalProperties"] = true
                fieldSchema["description"] = field.name
            }
            type.isEnum -> {
                fieldSchema["type"] = "string"
                fieldSchema["enum"] = type.enumConstants.map { (it as Enum<*>).name }
                fieldSchema["description"] = field.name
            }
            else -> {
                // Complex type - reference
                fieldSchema["\$ref"] = "#/components/schemas/${type.simpleName}"
            }
        }

        // Handle nullable types
        if (type.name.startsWith("java.lang.") && !type.isPrimitive) {
            fieldSchema["nullable"] = true
        }

        return fieldSchema
    }

    private fun getTypeName(clazz: Class<*>): String {
        return when (clazz) {
            String::class.java -> "string"
            Int::class.java, Integer::class.java -> "integer"
            Long::class.java, java.lang.Long::class.java -> "integer"
            Double::class.java, java.lang.Double::class.java -> "number"
            Float::class.java, java.lang.Float::class.java -> "number"
            Boolean::class.java, java.lang.Boolean::class.java -> "boolean"
            else -> "object"
        }
    }

    private fun getCollectionItemType(field: java.lang.reflect.Field): Class<*> {
        return try {
            val genericString = field.genericType.toString()
            when {
                genericString.contains("java.util.List<") -> {
                    val typeName = genericString.substringAfter("java.util.List<").substringBefore(">")
                    Class.forName(typeName)
                }
                genericString.contains("[]") -> {
                    val typeName = genericString.substringBefore("[]")
                    Class.forName(typeName)
                }
                else -> Any::class.java
            }
        } catch (e: Exception) {
            Any::class.java
        }
    }

    private fun <T> getAnnotationValue(annotation: Annotation, property: String): T? {
        return try {
            val method = annotation.annotationClass.java.getDeclaredMethod(property)
            @Suppress("UNCHECKED_CAST")
            method.invoke(annotation) as? T
        } catch (e: Exception) {
            null
        }
    }

    private fun generateExample(clazz: Class<*>): Map<String, Any?> {
        return try {
            val instance = clazz.getDeclaredConstructor().newInstance()
            val example = mutableMapOf<String, Any?>()

            clazz.declaredFields.forEach { field ->
                if (!Modifier.isStatic(field.modifiers)) {
                    field.isAccessible = true
                    val value = field.get(instance)

                    example[field.name] = when (value) {
                        null -> getDefaultValue(field.type)
                        is String -> if (field.name.lowercase().contains("email")) "user@example.com"
                        else if (field.name.lowercase().contains("name")) "John Doe"
                        else "example"
                        is Number -> when (field.type) {
                            Int::class.java, Integer::class.java -> 42
                            Long::class.java, java.lang.Long::class.java -> 42L
                            Double::class.java, java.lang.Double::class.java -> 42.0
                            Float::class.java, java.lang.Float::class.java -> 42.0f
                            else -> value
                        }
                        is Boolean -> true
                        else -> value.toString()
                    }
                }
            }

            example
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun getDefaultValue(type: Class<*>): Any? {
        return when (type) {
            String::class.java -> ""
            Int::class.java, Integer::class.java -> 0
            Long::class.java, java.lang.Long::class.java -> 0L
            Double::class.java, java.lang.Double::class.java -> 0.0
            Float::class.java, java.lang.Float::class.java -> 0.0f
            Boolean::class.java, java.lang.Boolean::class.java -> false
            else -> null
        }
    }

    private fun generateStubSchema(className: String): Map<String, Any> {
        return mapOf(
            "title" to className,
            "type" to "object",
            "description" to "Schema for $className",
            "properties" to emptyMap<String, Any>(),
            "required" to emptyList<String>()
        )
    }

    fun generateOpenAPIComponent(schemas: Map<String, Map<String, Any>>): Map<String, Any> {
        return mapOf(
            "openapi" to "3.0.0",
            "info" to mapOf(
                "title" to "Generated API",
                "version" to "1.0.0",
                "description" to "Auto-generated from Pydantic models"
            ),
            "components" to mapOf(
                "schemas" to schemas
            )
        )
    }
}