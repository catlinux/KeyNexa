plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":ui:shared"))
    implementation(project(":core:session"))
    implementation(project(":core:sync"))
    implementation(project(":core:kdbx"))
    implementation(project(":platform:windows"))

    implementation(compose.desktop.windows_x64)
    implementation(compose.material3)
    implementation(compose.components.resources)

    testImplementation(libs.junit)
}

compose.resources {
    packageOfResClass = "io.github.catlinux.keynexa.desktop.app.generated.resources"
}

// The Compose resources plugin adds its generated sources to the main source set. The ktlint task's input snapshot
// does not track the filter, so without this it would reuse a stale report that still lists the generated files.
tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
    exclude { element ->
        val path = element.file.path.replace('\\', '/')
        path.contains("/build/generated/")
    }
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}

// The product version lives in the VERSION file at the repository root; the installer takes it from there.
val productVersion = rootProject.file("VERSION").readText().trim()

compose.desktop {
    application {
        mainClass = "io.github.catlinux.keynexa.desktop.app.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi)
            packageName = "KeyNexa"
            packageVersion = productVersion
            description = "KeyNexa password manager"
            vendor = "CatLinux"
            copyright = "CatLinux"
            // jlink builds a runtime with only the modules the app needs; these two are loaded by reflection.
            modules("jdk.unsupported", "java.sql")
            windows {
                menuGroup = "KeyNexa"
                // Installed for the current user only: no administrator rights, and no UAC prompt.
                perUserInstall = true
                // %LOCALAPPDATA%\KeyNexa-app, apart from %LOCALAPPDATA%\KeyNexa where the app keeps its data, so
                // uninstalling the program never touches the user's copies, settings or credentials. jpackage takes
                // a single folder name here: a nested path is rejected by the MSI linker.
                installationPath = "KeyNexa-app"
                shortcut = true
                dirChooser = true
                iconFile.set(project.file("packaging/windows/keynexa.ico"))
                // Fixed, so a newer version upgrades the installed one instead of installing next to it.
                upgradeUuid = "8d6f1a52-3c0e-4f5e-9b7a-6a1c2d4e8f10"
            }
        }
    }
}

// The portable ZIP is the distributable folder (app and runtime) zipped, with the version in its name.
val portableZip by tasks.registering(Zip::class) {
    dependsOn("createDistributable")
    from(layout.buildDirectory.dir("compose/binaries/main/app"))
    archiveFileName.set("KeyNexa-windows-x64-$productVersion.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}
