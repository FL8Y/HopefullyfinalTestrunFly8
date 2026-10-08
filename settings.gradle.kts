import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // The pinned VirtualApp-derived library declares its own legacy repositories.
    // PREFER_PROJECT lets that isolated module resolve its own declared dependencies.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "FLY8"
include(":app")
include(":virtual-engine")
project(":virtual-engine").projectDir = file("vendor/virtualapp16/lib")
