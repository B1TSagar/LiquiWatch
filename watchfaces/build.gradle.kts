// The three LiquiWatch watch faces, in Google's Watch Face Format (XML only, no code:
// since January 2026 Wear OS only installs watch faces made this way). Each flavor is its own
// little app: pick one in Build Variants (e.g. "liquidDebug") and run it, or
// ./gradlew :watchfaces:installLiquidDebug (installCoverflowDebug, installExpeditionDebug).
plugins {
    id("com.android.application")
}

android {
    namespace = "com.liquiwatch.watchfaces"
    compileSdk = 35

    defaultConfig {
        // Wear OS 5 at least: the faces are Watch Face Format version 2 or newer (weather, flavors
        // and more), which Wear OS 4 doesn't read. With 33 here a Wear OS 4 watch would install a
        // face and then silently leave it out of the watch face list; with 34 it refuses the
        // install instead. (Expedition needs Wear OS 6: see its flavor.)
        minSdk = 34
        targetSdk = 34
        versionCode = 1
        // (Which round of changes this is: the trace recorder in tools/trace notes it.)
        versionName = "1.0-r37"
        // The Watch Face Format version each face is written in (the manifest's
        // com.google.wear.watchface.format.version). Liquid and Covers are version 2.
        manifestPlaceholders["wffVersion"] = "2"
    }

    flavorDimensions += "face"
    productFlavors {
        create("liquid") {
            dimension = "face"
            applicationId = "com.liquiwatch.face.liquid"
        }
        create("coverflow") {
            dimension = "face"
            applicationId = "com.liquiwatch.face.covers"
        }
        create("expedition") {
            dimension = "face"
            applicationId = "com.liquiwatch.face.expedition"
            // Expedition is Watch Face Format version 4, which needs Wear OS 6 (One UI 8 Watch):
            // it takes the album colour from Cover Display's Song progress for its hands' lines
            // (references and colour expressions, new in version 4). On Wear OS 5 the watch
            // installs it but leaves it out of the watch face list.
            manifestPlaceholders["wffVersion"] = "4"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Resources only: nothing to shrink, and none may be removed.
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}
