package com.pydantic.plugin

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

open class PydanticExtension  @Inject constructor(objectFactory: ObjectFactory) {
    val enabled: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(true)

    val strictValidation: Property<Boolean> = objectFactory.property(Boolean::class.java)
        .convention(false)

    val generateSchemas: Property<Boolean> = objectFactory.property(Boolean::class.java)
        .convention(true)

    val schemaFormats: ListProperty<String> = objectFactory.listProperty(String::class.java)
        .convention(listOf("json", "yaml"))

    val validationPackage: Property<String> = objectFactory.property(String::class.java)
        .convention("com.pydantic.generated")

    val includeValidationInBuild: Property<Boolean> = objectFactory.property(Boolean::class.java)
        .convention(true)

    val failOnValidationError: Property<Boolean> = objectFactory.property(Boolean::class.java)
        .convention(true)

    // Field naming strategies
    val fieldNamingStrategy: Property<FieldNamingStrategy> = objectFactory.property(FieldNamingStrategy::class.java)
        .convention(FieldNamingStrategy.SNAKE_CASE)

    // Custom configurations
    val customValidators: ListProperty<String> = objectFactory.listProperty(String::class.java)
        .convention(emptyList())

    val excludeClasses: ListProperty<String> = objectFactory.listProperty(String::class.java)
        .convention(emptyList())

    val useDelegates: Property<Boolean> = objectFactory.property(Boolean::class.java)
        .convention(false)

    val generateDelegates: Property<Boolean> = objectFactory.property(Boolean::class.java)
        .convention(true)

    fun delegates(action: Action<in DelegatesConfiguration>) {
        action.execute(delegatesConfiguration)
    }

    private val delegatesConfiguration = objectFactory.newInstance(DelegatesConfiguration::class.java)

    enum class FieldNamingStrategy {
        SNAKE_CASE,
        CAMEL_CASE,
        KEBAB_CASE,
        PASCAL_CASE
    }

    fun customValidators(action: Action<in ListProperty<String>>) {
        action.execute(customValidators)
    }

    fun excludeClasses(action: Action<in ListProperty<String>>) {
        action.execute(excludeClasses)
    }

    open class DelegatesConfiguration @Inject constructor(objectFactory: ObjectFactory) {
        val generateHelpers: Property<Boolean> = objectFactory.property(Boolean::class.java)
            .convention(true)

        val strictNullHandling: Property<Boolean> = objectFactory.property(Boolean::class.java)
            .convention(true)
    }
}

