import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files

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

// Fixed, so a newer version upgrades the installed one instead of installing next to it.
val msiUpgradeUuid = "8d6f1a52-3c0e-4f5e-9b7a-6a1c2d4e8f10"

// %LOCALAPPDATA%\KeyNexa-app, apart from %LOCALAPPDATA%\KeyNexa where the app keeps its data, so uninstalling the
// program never touches the user's copies, settings or credentials. jpackage takes a single folder name here: a
// nested path is rejected by the MSI linker.
val msiInstallFolder = "KeyNexa-app"

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
                installationPath = msiInstallFolder
                shortcut = true
                dirChooser = true
                iconFile.set(project.file("packaging/windows/keynexa.ico"))
                upgradeUuid = msiUpgradeUuid
            }
        }
    }
}

// The MSI is built here rather than by Compose's packageMsi, because jpackage's own template does not remember the
// folder the user chose: an upgrade proposed the default one again. Compose empties jpackage's --resource-dir inside
// its task, with no point to add a template in between, so this runs jpackage itself on the app image Compose builds.
// The template is jpackage's own, patched at build time (never copied into the repository): the folder is stored
// under HKCU when installing and read back with a RegistrySearch before the folder is resolved, the usual WiX pattern.
// Installations made before this change did not store it, so the first upgrade from them still asks once.
val msiResources = layout.buildDirectory.dir("msi-resources")
val jdk17 =
    javaToolchains
        .launcherFor {
            languageVersion.set(JavaLanguageVersion.of(17))
        }.map { it.metadata.installationPath }

val rememberInstallDir by tasks.registering {
    val javaHome = jdk17
    val target = msiResources.map { it.file("main.wxs") }
    outputs.file(target)
    doLast {
        val home = javaHome.get().asFile.absolutePath
        val template =
            FileSystems.newFileSystem(URI.create("jrt:/"), mapOf("java.home" to home)).use { jrt ->
                String(
                    Files.readAllBytes(
                        jrt.getPath("modules", "jdk.jpackage", "jdk/jpackage/internal/resources/main.wxs"),
                    ),
                    Charsets.UTF_8,
                )
            }
        val key = """Software\$(var.JpAppVendor)\$(var.JpAppName)"""
        val patches =
            listOf(
                "    <!-- Standard required root -->" to
                    """    <Property Id="INSTALLDIR">
      <RegistrySearch Id="KnPreviousInstallDir" Root="HKCU" Key="$key" Name="InstallDir" Type="raw"/>
    </Property>

    <!-- Standard required root -->""",
                """      <ComponentGroupRef Id="FileAssociations"/>""" to
                    """      <ComponentGroupRef Id="FileAssociations"/>
      <ComponentRef Id="KnRememberInstallDir"/>""",
                """    <UIRef Id="JpUI"/>""" to
                    """    <DirectoryRef Id="INSTALLDIR">
      <Component Id="KnRememberInstallDir" Guid="*">
        <RegistryValue Root="HKCU" Key="$key" Name="InstallDir" Type="string" Value="[INSTALLDIR]" KeyPath="yes"/>
      </Component>
    </DirectoryRef>

    <UIRef Id="JpUI"/>""",
            )
        val patched =
            patches.fold(template) { text, (anchor, replacement) ->
                if (anchor !in text) throw GradleException("jpackage's main.wxs changed: '$anchor' is gone")
                text.replace(anchor, replacement)
            }
        target.get().asFile.apply {
            parentFile.mkdirs()
            writeText(patched)
        }
    }
}

val windowsMsi by tasks.registering(Exec::class) {
    description = "Builds the Windows installer (MSI) from the app image, remembering the installation folder."
    group = "distribution"
    dependsOn("createDistributable", rememberInstallDir)
    val appImage = layout.buildDirectory.dir("compose/binaries/main/app/KeyNexa")
    val destination = layout.buildDirectory.dir("msi")
    // Compose downloads the WiX tools for its own packaging; jpackage finds them on the PATH.
    val wix =
        tasks
            .named<org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask>("packageMsi")
            .flatMap { it.wixToolsetDir }
    inputs.dir(appImage)
    inputs.dir(msiResources)
    outputs.dir(destination)
    doFirst {
        delete(destination)
        val wixBin = wix.orNull?.asFile?.absolutePath
        if (wixBin != null) environment("PATH", wixBin + File.pathSeparator + System.getenv("PATH"))
    }
    executable(jdk17.get().file("bin/jpackage.exe").asFile)
    args(
        "--type",
        "msi",
        "--app-image",
        appImage.get().asFile.absolutePath,
        "--dest",
        destination.get().asFile.absolutePath,
        "--resource-dir",
        msiResources.get().asFile.absolutePath,
        "--name",
        "KeyNexa",
        "--app-version",
        productVersion,
        "--vendor",
        "CatLinux",
        "--description",
        "KeyNexa password manager",
        "--copyright",
        "CatLinux",
        "--icon",
        project.file("packaging/windows/keynexa.ico").absolutePath,
        "--install-dir",
        msiInstallFolder,
        "--win-per-user-install",
        "--win-dir-chooser",
        "--win-shortcut",
        "--win-menu",
        "--win-menu-group",
        "KeyNexa",
        "--win-upgrade-uuid",
        msiUpgradeUuid,
    )
}

// The portable ZIP is the distributable folder (app and runtime) zipped, with the version in its name.
val portableZip by tasks.registering(Zip::class) {
    dependsOn("createDistributable")
    from(layout.buildDirectory.dir("compose/binaries/main/app"))
    archiveFileName.set("KeyNexa-windows-x64-$productVersion.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}
