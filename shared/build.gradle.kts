import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// What the faces' companion apps (phone and watch) say to each other. Nothing here is shared
// with Cover Display.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.liquiwatch.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // ./gradlew :shared:testDebugUnitTest (what the two halves send each other, there and back)
    testImplementation("junit:junit:4.13.2")
}
