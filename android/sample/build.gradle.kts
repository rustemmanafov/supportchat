plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.supportchat.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.supportchat.sample"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        // The emulator reaches the host machine's localhost at 10.0.2.2.
        buildConfigField("String", "SUPPORTCHAT_SERVER", "\"http://10.0.2.2:4000\"")
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":supportchat"))
}
