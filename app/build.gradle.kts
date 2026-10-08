plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.keybuilder"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.keybuilder"
        minSdk = 26
        // 34 on purpose: targeting 35 forces edge-to-edge drawing, which needs
        // extra inset handling in the keyboard window. Revisit before publishing.
        targetSdk = 34
        versionCode = 10
        versionName = "0.10.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.savedstate)
    implementation(libs.kotlinx.serialization.json)
    // FileProvider and the "send a picture from the keyboard" helpers.
    implementation(libs.androidx.core.ktx)
    // Loads pictures and plays GIFs in the panels.
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    testImplementation(libs.junit)
}
