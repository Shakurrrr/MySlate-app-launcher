plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") // ← this is the missing piece

}

repositories {
    // keep this while we stabilize; we can remove later once settings.gradle.kts is canonical
    google()
    mavenCentral()
}

android {
    // … your existing config

    // For App Bundles (.aab): disable Play splits
    bundle {
        abi { enableSplit = false }
        density { enableSplit = false }
        language { enableSplit = false }   // <-- language belongs ONLY here
    }

    // For APK builds: disable local splits
    splits {
        abi { isEnable = false }
        density { isEnable = false }
    }
}

android {
    namespace = "com.myslates.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.myslates.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {

        implementation("androidx.security:security-crypto:1.1.0-alpha06") // or newer


    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // For `kotlinx.coroutines.tasks.await`
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // AndroidX
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation(libs.androidx.ui.graphics.android)
}
