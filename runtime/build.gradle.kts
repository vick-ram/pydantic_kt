plugins {
    `java-library`
    `maven-publish`
    kotlin("jvm") version "2.2.21"
}

group = "com.pydantic"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.kotlin)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "Pydantic Runtime",
            "Implementation-Version" to project.version
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("runtime") {
            from(components["java"])
            artifactId = "pydantic-runtime"
            version = project.version.toString()
        }
    }
}