import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The watch faces' own companion, watch half: the compass (its bezel complication and the live
// compass screen) and the Expedition face's panels (their pages, and the Samsung Health readings
// the phone half sends). A separate app from Cover Display; the faces only lean on Cover Display for
// the playing song's blurred cover and colour.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.liquiwatch.wear"
    compileSdk = 35

    defaultConfig {
        // MUST be identical to :phone's applicationId (Wear Data Layer requirement), and
        // different from Cover Display's: they're separate apps.
        applicationId = "com.liquiwatch.companion"
        minSdk = 30 // Wear OS 3+ : Galaxy Watch4 and newer
        targetSdk = 34
        versionCode = 1
        // (Which round of changes this is: the trace recorder in tools/trace notes it.)
        versionName = "1.0-r38"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Complications: the compass and the face's panel pages. (1.3.0 hands updates straight to
    // the watch's complication system on Wear OS 6: see FaceUpdates.)
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.3.0")

    // The phone half's Samsung Health readings.
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // ./gradlew :wear:testDebugUnitTest (the compass's arithmetic and the beats)
    testImplementation("junit:junit:4.13.2")
}
