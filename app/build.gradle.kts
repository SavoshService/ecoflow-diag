plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ua.savosh.ecoflowdiag"
    compileSdk = 35

    defaultConfig {
        applicationId = "ua.savosh.ecoflowdiag"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "2.3"
    }

    // Постійний ключ підпису: нові збірки встановлюються поверх старих без видалення застосунку.
    signingConfigs {
        create("savosh") {
            storeFile = file("savosh.keystore")
            storePassword = "savosh123"
            keyAlias = "savosh"
            keyPassword = "savosh123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("savosh")
        }
        debug {
            signingConfig = signingConfigs.getByName("savosh")
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // MQTT-клієнт для хмари EcoFlow
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
}
