plugins {
    `java-gradle-plugin`
    `maven-publish`
    kotlin("jvm")
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


// Configure functional tests
val functionalTestSourceSet = sourceSets.create("functionalTest").apply {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

dependencies {
    implementation(project(":runtime"))
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    implementation(libs.ksp.api)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.kotlin)
    implementation(libs.jackson.yaml)
    implementation(libs.squareup.kotlinpoet)
    implementation(libs.squareup.kotlinpoet.ksp)
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

    implementation(libs.kotlin)
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

    testSourceSets(functionalTestSourceSet, sourceSets.testFixtures.get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging { showStandardStreams = true }
    dependsOn(tasks.pluginUnderTestMetadata)
}

val dir  = layout.buildDirectory.get().asFile
val copyFunctionalTestResources by tasks.registering(Copy::class) {
    from("src/functionalTest/resources")
//    into("$buildDir/functionalTest")
    into("$dir/functionalTest")
}

val functionalTest by tasks.registering(Test::class) {
    description = "Runs functional tests"
    group = "verification"

    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
    useJUnitPlatform()

    dependsOn("pluginUnderTestMetadata", "jar")
    dependsOn(":runtime:publishToMavenLocal")
    dependsOn("publishToMavenLocal")
    dependsOn(copyFunctionalTestResources)

    systemProperty("test.project.dir", projectDir.path)
    systemProperty("test.build.dir", dir.path)
    systemProperty("org.gradle.configuration-cache", "false")

    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
        showCauses = true
        showStackTraces = true
    }
}

configurations["functionalTestImplementation"].extendsFrom(configurations.testImplementation.get())
configurations["functionalTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

tasks.check {
    dependsOn(functionalTest)
}

// Make functional test source set available
gradlePlugin.testSourceSets(functionalTestSourceSet)
