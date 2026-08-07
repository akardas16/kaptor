pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kaptor"
include(":kaptor-core")
include(":kaptor-ui")
include(":kaptor-android")
include(":kaptor-no-op")
include(":sample-android")
include(":sample-ios-shared")
