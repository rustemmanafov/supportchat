plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    `maven-publish`
}

// JitPack passes its own coordinates (com.github.<owner>:<repo>:<tag>) through env vars.
group = System.getenv("GROUP") ?: "io.supportchat"
version = System.getenv("VERSION") ?: "1.0.1"

android {
    namespace = "io.supportchat.sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "SDK_VERSION", "\"1.0.1\"")
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    testOptions { unitTests.isReturnDefaultValues = true }

    publishing {
        singleVariant("release") { withSourcesJar() }
    }
}

// ./gradlew :supportchat:publishReleasePublicationToLocalRepository → supportchat/build/repo
// JitPack runs publishToMavenLocal; Maven Central needs signing + a Sonatype account.
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = System.getenv("ARTIFACT") ?: "supportchat-android"
            version = project.version.toString()
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("SupportChat Android SDK")
                description.set("Live chat for Android apps, powered by a SupportChat server.")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "local"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}

// No AndroidX, no third-party libraries: nothing can conflict with the host app.
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
