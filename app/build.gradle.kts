plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.giwnltrema.chuva"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.giwnltrema.chuva"
        minSdk = 26
        targetSdk = 35
        // O CI usa o número da execução, assim cada APK novo instala por cima do anterior.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"

        ndk {
            // Celulares atuais são arm64; x86_64 é para o emulador.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    // A chave de assinatura NÃO fica no repositório: o CI monta o arquivo a partir dos
    // secrets CHUVA_KEYSTORE_BASE64 / CHUVA_KEYSTORE_PASSWORD. Localmente, sem essas
    // variáveis, o release é assinado com a chave de debug (serve para testar).
    val keystorePath = System.getenv("CHUVA_KEYSTORE")
    signingConfigs {
        if (keystorePath != null) {
            create("personal") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("CHUVA_KEYSTORE_PASSWORD")
                keyAlias = "chuva"
                keyPassword = System.getenv("CHUVA_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("personal") ?: signingConfigs.getByName("debug")
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
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    implementation("org.maplibre.gl:android-sdk:11.8.0")

    testImplementation("junit:junit:4.13.2")
}
