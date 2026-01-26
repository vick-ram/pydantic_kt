plugins {
    // This is the root project, no plugins needed here usually
}

allprojects {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

subprojects {
    // Common configuration for all subprojects
}