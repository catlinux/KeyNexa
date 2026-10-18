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
include(":core:session")
include(":platform:jvm")
include(":platform:cloud")
include(":platform:android")
include(":ui:shared")
include(":apps:android")
include(":kotpass")
project(":kotpass").projectDir = file("third_party/kotpass")
