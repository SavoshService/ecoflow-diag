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
        versionCode = 16
        versionName = "2.5"
    }

    // Постійний ключ підпису: нові збірки встановлюються поверх старих без видалення застосунку.
    // Ключ і паролі НЕ зберігаються в коді:
    //   • на GitHub — беруться з GitHub Secrets (workflow кладе ключ у app/savosh.keystore
    //     і передає паролі через змінні оточення);
    //   • локально — з файлу app/savosh.keystore + паролів у ~/.gradle/gradle.properties
    //     (SAVOSH_STORE_PASSWORD / SAVOSH_KEY_ALIAS / SAVOSH_KEY_PASSWORD).
    // Якщо ключа немає — release збирається без підпису (debug працює завжди).
    val keystoreFile = file(System.getenv("KEYSTORE_FILE") ?: "savosh.keystore")
    val hasKeystore = keystoreFile.exists()

    signingConfigs {
        if (hasKeystore) {
            create("savosh") {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                    ?: (project.findProperty("SAVOSH_STORE_PASSWORD") as String?) ?: ""
                keyAlias = System.getenv("KEY_ALIAS")
                    ?: (project.findProperty("SAVOSH_KEY_ALIAS") as String?) ?: "savosh"
                keyPassword = System.getenv("KEY_PASSWORD")
                    ?: (project.findProperty("SAVOSH_KEY_PASSWORD") as String?) ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasKeystore) signingConfig = signingConfigs.getByName("savosh")
        }
        debug {
            if (hasKeystore) signingConfig = signingConfigs.getByName("savosh")
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

    // Фонова перевірка оновлень
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // MQTT-клієнт для хмари EcoFlow
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
}
