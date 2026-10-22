import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The watch faces' own companion, phone half: reads sleep score, Energy score, blood oxygen and
// active calories from Samsung Health and hands them to the watch half for the Expedition face's
// panels. A separate app from Cover Display.
//
// Samsung Health is read with Samsung's Health Data SDK, which Samsung doesn't publish to a
// package repository: its .aar sits in phone/libs (see libs/README.txt). Without it the app
// still builds, and the health pages show "--".
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val samsungSdk = fileTree("libs") { include("samsung-health-data-api*.aar") }.files.sortedBy { it.name }.lastOrNull()

android {
    namespace = "com.liquiwatch.phone"
    compileSdk = 35

    defaultConfig {
        // MUST be identical to :wear's applicationId (Wear Data Layer requirement), and
        // different from Cover Display's: they're separate apps.
        applicationId = "com.liquiwatch.companion"
        minSdk = 29 // Samsung's Health Data SDK needs Android 10
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("boolean", "SAMSUNG_SDK", (samsungSdk != null).toString())
    }

    sourceSets["main"].java.srcDir(if (samsungSdk != null) "src/samsung/kotlin" else "src/nosamsung/kotlin")

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
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))

    if (samsungSdk != null) {
        implementation(files(samsungSdk))
        // What the Samsung SDK needs alongside it (an .aar file carries no dependencies of its own).
        implementation("com.google.code.gson:gson:2.11.0")
        implementation("org.jetbrains.kotlin:kotlin-parcelize-runtime:2.1.0")
    }

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")

    // Reading Samsung Health or the weather once, in the background, when the watch asks.
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // ./gradlew :phone:testDebugUnitTest (the air quality index, and reading Open-Meteo's answers)
    testImplementation("junit:junit:4.13.2")
}
