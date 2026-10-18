import com.android.build.api.artifact.SingleArtifact

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

    flavorDimensions += "distribution"
    productFlavors {
        create("offline") { dimension = "distribution" }
        create("cloud") { dimension = "distribution" }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
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

base.archivesName = "keynexa-android"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":platform:android"))
    implementation(project(":core:kdbx"))
    implementation(project(":core:session"))
    implementation(project(":ui:shared"))

    // The cloud client and its credential store exist only in the cloud flavor; the offline APK must not carry them.
    "cloudImplementation"(project(":platform:cloud"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}

// The offline build must never carry the Internet permission, even if a library adds it: a check on the merged
// manifest catches both cases. The cloud build is checked the other way round.
androidComponents {
    onVariants { variant ->
        val expectations =
            mapOf(
                "offlineRelease" to ("verifyOfflineHasNoInternet" to false),
                "offlineDebug" to ("verifyOfflineDebugHasNoInternet" to false),
                "cloudRelease" to ("verifyCloudHasInternet" to true),
            )
        val (taskName, expectInternet) = expectations[variant.name] ?: return@onVariants
        val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val expectation = if (expectInternet) "contain" else "lack"
        val verify =
            tasks.register(taskName) {
                group = "verification"
                description = "Fails when the merged ${variant.name} manifest does not $expectation INTERNET."
                inputs.file(manifest)
                doLast {
                    val text = manifest.get().asFile.readText()
                    val hasInternet = text.contains("android.permission.INTERNET")
                    check(hasInternet == expectInternet) {
                        "Merged ${variant.name} manifest has INTERNET=$hasInternet but expected $expectInternet."
                    }
                }
            }
        tasks.matching { it.name == "check" }.configureEach { dependsOn(verify) }
    }
}
