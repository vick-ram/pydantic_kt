plugins {
    kotlin("jvm") version "2.2.21"
}

repositories {
    mavenLocal()
    mavenCentral()
}

// Test the plugin locally
buildscript {
    dependencies {
        // Use the plugin from local build
        classpath(files("../../plugin/build/libs/plugin-1.0.0.jar"))
    }
}

apply(plugin = "com.pydantic")

pydantic {
    enabled = true
    useDelegates = true
    strictValidation = false
    generateSchemas = true
    schemaFormats = listOf("json", "yaml")

    delegates {
        generateHelpers = true
        strictNullHandling = true
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}