# Pydantic-kt: Pydantic-like Data Validation for Kotlin

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/your-repo/pydantic-kt/actions)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Kotlin Version](https://img.shields.io/badge/kotlin-1.9.0-blue.svg)](https://kotlinlang.org/)
[![Gradle Version](https://img.shields.io/badge/gradle-8.x-green.svg)](https://gradle.org/)

Bringing robust data validation and serialization inspired by Python's Pydantic to Kotlin. Define your data models with clear validation rules and let Pydantic-kt handle the rest, ensuring data integrity and generating useful artifacts like JSON Schemas.

**Note**: This project is currently named "Pydantic-kt" to reflect its inspiration. We plan to rename it to a more unique and Kotlin-centric name (e.g., "Kantic", "Kotlantic", "ValiKot") in the near future.

## ✨ Features

*   ✅ **Declarative Validation**: Define validation rules directly within your data classes using annotations or property delegates.
*   ⚡ **Runtime Validation**: Automatically validate incoming data against your defined rules, ensuring data integrity at runtime.
*   🔄 **Serialization & Deserialization**: Seamlessly convert between Kotlin objects and JSON, with validation applied during deserialization.
*   📝 **JSON Schema Generation**: Automatically generate OpenAPI-compatible JSON Schemas from your models, aiding documentation and API design.
*   ⚙️ **Gradle Plugin**: Integrate data model processing directly into your build pipeline, automating code generation and validation tasks.
*   🧩 **Flexible Model Definition**: Supports two primary ways to define models:
    *   **Annotation-driven**: Using `@Serializable` and `@Field` annotations on data classes.
    *   **Property Delegate-driven**: Using `by Field.type(...)` for more dynamic or concise definitions.
*   🚀 **Compile-time Safety**: Leverage Kotlin's strong type system for early error detection and better code maintainability.

## 💡 Why Pydantic-kt?

In modern applications, data validation is crucial. Pydantic-kt aims to:
*   **Reduce Boilerplate**: Eliminate repetitive manual validation code.
*   **Improve Data Integrity**: Ensure your application always works with valid data.
*   **Enhance Developer Experience**: Provide a clear, declarative way to define data constraints.
*   **Automate Documentation**: Keep your API documentation (via JSON Schema) in sync with your code.
*   **Bridge the Gap**: Offer a familiar Pydantic-like experience for developers coming from Python.

## 🚀 Getting Started

### 1. Add the Plugin

First, add the Pydantic-kt Gradle plugin to your project.

**`settings.gradle.kts`** (recommended for plugin management):
```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenLocal() // If you're building the plugin locally
    }
}
```

**`build.gradle.kts`** (root project, if not using `settings.gradle.kts` for pluginManagement):
```kotlin
buildscript {
    repositories {
        mavenLocal() // If you're building the plugin locally
        mavenCentral()
    }
    dependencies {
        classpath("com.pydantic:pydantic-plugin:1.0.0") // Replace with actual version
    }
}
```

### 2. Apply and Configure the Plugin

Apply the plugin in your module's `build.gradle.kts` file and configure it using the `pydantic` extension block.

**`build.gradle.kts`** (your module):
```kotlin
plugins {
    kotlin("jvm") version "1.9.0" // Or your Kotlin version
    id("com.pydantic") version "1.0.0" // Replace with actual version
}

repositories {
    mavenCentral()
    mavenLocal() // If you're building the plugin locally
}

pydantic {
    enabled.set(true) // Enable/disable the plugin (default: true)
    useDelegates.set(false) // Use property delegates for model definition (default: false)
    strictValidation.set(false) // Enable strict validation for all models (default: false)
    failOnValidationError.set(true) // Fail the build on validation errors (default: true)
    validationPackage.set("com.example.generated") // Package for generated validators (default: com.pydantic.generated)
    generateSchemas.set(true) // Enable JSON schema generation (default: true)
    schemaFormats.set(listOf("JSON_SCHEMA_DRAFT_7")) // List of schema formats to generate
}

dependencies {
    // Other dependencies...
}
```

### 3. Add Runtime Dependency

Include the Pydantic-kt runtime library in your module's dependencies.

**`build.gradle.kts`** (your module):
```kotlin
dependencies {
    implementation("com.pydantic:pydantic-runtime:1.0.0") // Replace with actual version
    // For JSON serialization/deserialization
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
}
```

## 📝 Usage Examples

### Annotation-based Model

Define your data classes with `@Serializable` and add validation rules using `@Field` annotations.

```kotlin
package com.example.models

import com.pydantic.annotation.Serializable
import com.pydantic.annotation.Field

@Serializable
data class User(
    @Field(minLength = 2, maxLength = 50)
    val name: String,
    @Field(min = 0, max = 150)
    val age: Int,
    @Field(pattern = "^[A-Za-z0-9+_.-]+@(.+)$")
    val email: String,
    val isActive: Boolean = true,
    val tags: List<String>? = null
)
```

### Delegate-based Model

For a more concise syntax or when you prefer property delegates, you can define models like this:

```kotlin
package com.example.models

import com.pydantic.runtime.delegates.Field

class Product {
    var id: String by Field.string(minLength = 5, maxLength = 10)
    var name: String by Field.string(minLength = 3)
    var price: Double by Field.double(min = 0.01)
    var description: String? by Field.string(maxLength = 200).optional()
    var quantity: Int by Field.int(min = 0).default(0)
}
```
*Note: To use delegate-based models, ensure `pydantic { useDelegates.set(true) }` is configured in your `build.gradle.kts`.*

### Validation

Pydantic-kt generates validator classes for your models. You can use the `ModelValidator` to perform runtime validation.

```kotlin
package com.example.app

import com.pydantic.runtime.validation.ModelValidator
import com.example.models.User // Your defined model

fun main() {
    val validator = ModelValidator()

    // Valid user
    val validUser = User("Alice", 30, "alice@example.com")
    val validResult = validator.validate(validUser)
    println("Valid User Result: ${validResult.isValid}") // true

    // Invalid user (name too short, age out of range, invalid email)
    val invalidUser = User("A", 200, "invalid-email")
    val invalidResult = validator.validate(invalidUser)
    println("Invalid User Result: ${invalidResult.isValid}") // false
    invalidResult.errors.forEach { error ->
        println("  Error on ${error.field}: ${error.message}")
    }
    /* Output:
       Error on name: String length must be between 2 and 50
       Error on age: Value must be between 0 and 150
       Error on email: String does not match pattern ^[A-Za-z0-9+_.-]+@(.+)$
    */
}
```

### Serialization & Deserialization

Pydantic-kt models are designed to work seamlessly with popular JSON libraries like Jackson.

```kotlin
package com.example.app

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.pydantic.runtime.validation.ModelValidator
import com.example.models.User // Your defined model

fun main() {
    val mapper = ObjectMapper().registerModule(KotlinModule())
    val validator = ModelValidator()

    val user = User("Jane Doe", 25, "jane.doe@example.com")

    // Serialize to JSON
    val json = mapper.writeValueAsString(user)
    println("Serialized JSON: $json")
    // Output: {"name":"Jane Doe","age":25,"email":"jane.doe@example.com","isActive":true,"tags":null}

    // Deserialize from JSON and validate
    val jsonString = """{"name":"Bob","age":40,"email":"bob@example.com"}"""
    val deserializedUser = mapper.readValue<User>(jsonString)
    val validationResult = validator.validate(deserializedUser)

    if (validationResult.isValid) {
        println("Deserialized and Valid User: $deserializedUser")
    } else {
        println("Deserialization failed validation: ${validationResult.errors}")
    }

    // Example of invalid JSON data during deserialization
    val invalidJsonString = """{"name":"X","age":-5,"email":"bad-email"}"""
    try {
        val invalidDeserializedUser = mapper.readValue<User>(invalidJsonString)
        validator.validate(invalidDeserializedUser).errors.forEach {
            println("Deserialization validation error: ${it.message}")
        }
    } catch (e: Exception) {
        println("Caught exception during invalid deserialization: ${e.message}")
    }
}
```

## ⚙️ Gradle Tasks

The Pydantic-kt Gradle plugin provides several tasks to automate your workflow:

*   `generatePydanticValidators`: Generates Kotlin validator classes for your annotation-based Pydantic models.
*   `generatePydanticDelegates`: Generates helper code for your property delegate-based Pydantic models.
*   `validatePydanticModels`: Runs a validation check on all detected Pydantic models in your project, reporting any issues. This task can be configured to fail the build on errors.
*   `generateJsonSchemas`: Generates JSON Schema files (e.g., Draft 7) for your Pydantic models, typically outputting to `build/schemas`.

## 🗺️ Roadmap & Future

We have exciting plans for Pydantic-kt:

*   **Project Renaming**: A new, unique name for the project is coming soon!
*   **More Validation Types**: Expand the range of supported validation rules and annotations (e.g., UUID, URL, custom validators).
*   **Improved Error Reporting**: More detailed and user-friendly validation error messages.
*   **KSP Integration**: Leverage Kotlin Symbol Processing for more powerful and flexible compile-time code generation.
*   **IDE Support**: Enhance the developer experience with better IDE integration (e.g., auto-completion for annotations).
*   **Performance Optimizations**: Continuously improve the performance of code generation and runtime validation.

## 🤝 Contributing

We welcome contributions from the community! Whether it's bug reports, feature requests, or code contributions, your help is greatly appreciated. Please refer to our [Contributing Guidelines](CONTRIBUTING.md) (to be created) for more information.

## 📄 License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
