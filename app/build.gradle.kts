plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.myslates.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.myslates.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.2"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // ✅ SIGNING CONFIG (use your passwords)
    signingConfigs {
        create("release") {
            storeFile = file("../myslates-release.jks")
            storePassword = "Elpercy123"
            keyAlias = "myslates"
            keyPassword = "Elpercy123"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // ✅ Disable splits so you get 1 APK
    splits {
        abi {
            isEnable = false
        }
        density {
            isEnable = false
        }
    }

    // ✅ For AAB bundles (Play Store)
    bundle {
        abi { enableSplit = false }
        density { enableSplit = false }
        language { enableSplit = false }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
