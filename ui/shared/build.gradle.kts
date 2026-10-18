plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    androidTarget()
    jvm("desktop")
    jvmToolchain(17)

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
            }
        }
        val desktopMain by getting {
            // The shared font lives here so both the Android resources and the desktop classpath find it.
            resources.srcDir("src/commonRes")
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.junit)
            }
        }
    }
}

android {
    namespace = "io.github.catlinux.keynexa.ui"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    sourceSets["main"].res.srcDirs("src/androidMain/res", "src/commonRes")

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
