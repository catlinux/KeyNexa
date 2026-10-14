plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

val appVersionName: String = rootProject.file("VERSION").readText().trim()
val appVersionCode: Int =
    appVersionName
        .split(".")
        .map(String::toInt)
        .let { (major, minor, patch) ->
            require(minor < 100 && patch < 100) {
                "VERSION minor and patch components must each be less than 100 to calculate versionCode"
            }
            major * 10000 + minor * 100 + patch
        }

android {
    namespace = "io.github.catlinux.keynexa.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.catlinux.keynexa"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    buildFeatures {
        compose = true
    }

    lint {
        lintConfig = rootProject.file("config/lint/lint.xml")
        warningsAsErrors = true
        abortOnError = true
        checkAllWarnings = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":platform-android"))
    implementation(project(":core-kdbx"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}
