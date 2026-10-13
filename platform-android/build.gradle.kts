plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.github.catlinux.keynexa.platform"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
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
    api(project(":core-sync"))
    api(project(":core-vault"))

    testImplementation(libs.junit)
}
