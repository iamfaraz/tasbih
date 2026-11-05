plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.tasbih.counter.mobile"
    compileSdk = 36

    defaultConfig {
        // Must match :wear. The Wearable Data Layer only connects apps that
        // share an applicationId and a signing certificate.
        applicationId = "app.tasbih.counter"
        minSdk = 26
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
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
}
