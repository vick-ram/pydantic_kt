package com.pydantic.runtime.models

import com.pydantic.runtime.serialization.SerializationUtils
import com.pydantic.runtime.validation.ValidationError
import com.pydantic.runtime.validation.ValidationException
import com.pydantic.runtime.validation.ValidationResult
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * Base class for all Pydantic models, similar to Python's pydantic.BaseModel.
 * Provides automatic validation, serialization, and utility methods.
 */
abstract class BaseModel {

    /**
     * Validates the model instance against all defined constraints.
     */
    open fun validate(): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        // Get all properties
        val properties = this::class.memberProperties

        properties.forEach { property ->
            if (property is KProperty1<*, *>) {
                val value = property.getter.call(this)

                // Check if property has validation annotations
                val propertyErrors = validateProperty(property, value)
                errors.addAll(propertyErrors)
            }
        }

        // Run custom validators
        val customErrors = validateCustom()
        errors.addAll(customErrors)

        return ValidationResult(
            modelName = this::class.simpleName ?: "Unknown",
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = emptyList()
        )
    }

    /**
     * Validates individual property.
     */
    protected open fun validateProperty(property: KProperty1<*, *>, value: Any?): List<ValidationError> {
        // Default implementation - can be overridden by generated code
        return emptyList()
    }

    /**
     * Custom validation logic that can be overridden by subclasses.
     */
    protected open fun validateCustom(): List<ValidationError> {
        return emptyList()
    }

    /**
     * Throws ValidationException if validation fails.
     */
    fun validateOrThrow() {
        val result = validate()
        if (!result.isValid) {
            throw ValidationException(result.errors)
        }
    }

    /**
     * Converts the model to a map.
     */
    fun toMap(): Map<String, Any?> {
        return this::class.memberProperties.associate { property ->
            property.name to property.getter.call(this)
        }
    }

    /**
     * Converts the model to a JSON string.
     */
    fun toJson(): String {
        return SerializationUtils.toJson(this)
    }

    /**
     * Creates a copy of the model with updated values.
     */
    fun copy(updates: Map<String, Any?>): BaseModel {
        val constructor = this::class.primaryConstructor
            ?: throw IllegalStateException("Model must have a primary constructor")

        val args = mutableMapOf<KParameter, Any?>()

        constructor.parameters.forEach { param ->
            val newValue = updates[param.name] ?: updates.getSnakeCase(param.name)
            if (newValue != null) {
                args[param] = newValue
            } else {
                // Use current value
                val currentValue = this::class.memberProperties
                    .find { it.name == param.name }
                    ?.getter?.call(this)
                args[param] = currentValue
            }
        }

        return constructor.callBy(args) as BaseModel
    }

    /**
     * Creates a copy with updated fields using DSL.
     */
    fun copy(block: ModelUpdater.() -> Unit): BaseModel {
        val updater = ModelUpdater().apply(block)
        return copy(updater.updates)
    }

    /**
     * Returns the model's JSON schema.
     */
    fun schema(): Map<String, Any> {
        // This would be implemented by generated code
        return emptyMap()
    }

    /**
     * Creates model from JSON.
     */
    companion object {
        inline fun <reified T : BaseModel> fromJson(json: String): T {
            return SerializationUtils.fromJson(json)
        }

        inline fun <reified T : BaseModel> fromMap(map: Map<String, Any?>): T {
            return SerializationUtils.fromMap(map)
        }

        inline fun <reified T : BaseModel> parseRaw(raw: String): T {
            return try {
                fromJson(raw)
            } catch (e: Exception) {
                throw ValidationException(
                    listOf(ValidationError(
                        field = null,
                        message = "Failed to parse JSON: ${e.message}",
                        code = "PARSE_ERROR",
                        value = raw
                    ))
                )
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BaseModel) return false
        if (this::class != other::class) return false

        return this.toMap() == other.toMap()
    }

    override fun hashCode(): Int {
        return toMap().hashCode()
    }

    override fun toString(): String {
        val props = toMap().entries.joinToString(", ") { (k, v) ->
            "$k=${v?.toString() ?: "null"}"
        }
        return "${this::class.simpleName}($props)"
    }
}

/**
 * DSL for updating models.
 */
class ModelUpdater {
    internal val updates = mutableMapOf<String, Any?>()

    operator fun <T> KProperty1<out BaseModel, T>.invoke(value: T) {
        updates[this.name] = value
    }
}

/**
 * Helper to get snake_case keys.
 */
private fun Map<String, Any?>.getSnakeCase(key: String?): Any? {
    val snakeKey = key?.replace(Regex("([a-z])([A-Z])"), "$1_$2")?.lowercase()
    return this[snakeKey] ?: this[key]
}

