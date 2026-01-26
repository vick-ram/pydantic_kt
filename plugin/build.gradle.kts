plugins {
    // Apply the Java Gradle plugin development plugin to add support for developing Gradle plugins
    `java-gradle-plugin`
    `maven-publish`
    // Apply the Kotlin JVM plugin to add support for Kotlin.
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    id("com.gradle.plugin-publish") version "1.2.0"
    id("java-test-fixtures")  // For sharing test utilities
}

group = "com.pydantic"
version = "1.0.0"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(project(":runtime"))
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    implementation(libs.ksp.api)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.kotlin)
    implementation(libs.jackson.yaml)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(gradleTestKit())  // For Gradle TestKit

    testFixturesImplementation(kotlin("stdlib"))
    testFixturesImplementation(libs.kotest.assertions.core)
    testFixturesImplementation(gradleTestKit())

    // Functional test dependencies
    "functionalTestImplementation"(kotlin("stdlib"))
    "functionalTestImplementation"(kotlin("test"))
    "functionalTestImplementation"(libs.kotest.runner.junit5)
    "functionalTestImplementation"(libs.kotest.assertions.core)
    "functionalTestImplementation"(gradleTestKit())
    "functionalTestImplementation"(project)  // The plugin itself

//    testFixturesImplementation(libs.kotest.runner.junit5)
//    testFixturesImplementation(libs.kotest.assertions.core)

    compileOnly(libs.kotlin)
}

// Configure testFixtures source set
java {
    withJavadocJar()
    withSourcesJar()
}

gradlePlugin {
    plugins {
        create("pydanticPlugin") {
            id = "com.pydantic"
            implementationClass = "com.pydantic.plugin.PydanticPlugin"
            displayName = "Pydantic-like Validation Plugin"
            description = "A Kotlin Gradle plugin for Pydantic-like data validation and serialization"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

// Configure functional tests
val functionalTestSourceSet = sourceSets.create("functionalTest").apply {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configurations["functionalTestImplementation"].extendsFrom(configurations.testImplementation.get())
configurations["functionalTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

val functionalTest by tasks.registering(Test::class) {
    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
    useJUnitPlatform()

    // Set up test environment
    systemProperty("gradle.user.home", temporaryDir.path)
    systemProperty("test.project.dir", projectDir.path)

    dependsOn(tasks.jar)  // Ensure plugin JAR is built
//    dependsOn(tasks.processFunctionalTestResources)

    // Copy functional test resources
    doFirst {
        copy {
            from("functionalTest")
            into("$buildDir/functionalTest")
            include("**/*.kt", "**/*.gradle.kts", "**/*.properties")
        }
    }
}
tasks.check {
    dependsOn(functionalTest)
}

// Make functional test source set available
gradlePlugin.testSourceSets(functionalTestSourceSet)
