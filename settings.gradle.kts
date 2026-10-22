pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LiquiWatch"

include(":watchfaces") // the three watch faces (Watch Face Format, no code)
include(":wear")       // the faces' companion, watch half: the compass, the Expedition face's panel pages
include(":phone")      // the faces' companion, phone half: Samsung Health readings for the panels
include(":shared")     // what the companion's two halves say to each other
