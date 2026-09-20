plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wakesync"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wakesync"
        minSdk = 30 // Wear OS 3.0+
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // UI Wear OS con Compose Material 3
    implementation("androidx.wear.compose:compose-material3:1.0.0-alpha20")
    implementation("androidx.wear.compose:compose-foundation:1.3.1")
    implementation("androidx.wear.compose:compose-navigation:1.3.1")

    // Horologist (Rotary Input)
    implementation("com.google.android.horologist:horologist-composables:0.6.9")
    implementation("com.google.android.horologist:horologist-compose-layout:0.6.9")

    // Sensores de salud y geoposicionamiento
    implementation("androidx.health:health-services-client:1.1.0-alpha03")
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // Persistencia local
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Arquitectura, ciclo de vida, corrutinas
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Pruebas unitarias
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("org.json:json:20231013")
}
