plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fl8y.guestfixture"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fl8y.guestfixture"
        minSdk = 26
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}
