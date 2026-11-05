plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.tasbih.counter.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
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
    api(libs.kotlinx.coroutines.android)
    implementation(platform(libs.compose.bom))
    api(libs.compose.foundation)
    api(libs.kotlinx.coroutines.play.services)
    api(libs.play.services.wearable)
    implementation(libs.adhan)

    testImplementation(libs.junit)
}
