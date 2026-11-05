plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.tasbih.counter.wear"
    compileSdk = 36

    defaultConfig {
        // Must match :mobile. See the note in mobile/build.gradle.kts.
        applicationId = "app.tasbih.counter"
        minSdk = 30 // Wear OS 3, the oldest Galaxy Watch release with Wear OS.
        targetSdk = 36
        // Set by the release workflow.
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = findProperty("versionName") as String? ?: "0.1.0"
    }
    // Both apps must share this key, or the Wearable Data Layer won't connect them.
    val keystore = System.getenv("TASBIH_KEYSTORE")
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("TASBIH_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TASBIH_KEY_ALIAS")
                keyPassword = System.getenv("TASBIH_KEYSTORE_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.compose.bom))
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.complications)
    implementation(libs.concurrent.futures)

    testImplementation(libs.junit)
}
