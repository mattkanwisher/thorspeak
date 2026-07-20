plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "nu.hyperworks.thorspeak"
    compileSdk = 34

    defaultConfig {
        applicationId = "nu.hyperworks.thorspeak"
        minSdk = 33
        targetSdk = 34
        versionCode = 4
        versionName = "0.1.3"
    }

    // Consistent signing across machines/CI so in-app updates install over
    // the previous version. Path+passwords come from env (CI secrets).
    val ksPath = System.getenv("THORSPEAK_KEYSTORE")
    if (ksPath != null && file(ksPath).exists()) {
        signingConfigs {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("THORSPEAK_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("THORSPEAK_KEY_ALIAS")
                keyPassword = System.getenv("THORSPEAK_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (ksPath != null && file(ksPath).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mlkit.text.japanese)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
}
