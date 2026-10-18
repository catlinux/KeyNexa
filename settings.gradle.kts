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

rootProject.name = "KeyNexa"

include(":core:vault")
include(":core:kdbx")
include(":core:sync")
include(":platform:cloud")
include(":platform:android")
include(":apps:android")
include(":kotpass")
project(":kotpass").projectDir = file("third_party/kotpass")
