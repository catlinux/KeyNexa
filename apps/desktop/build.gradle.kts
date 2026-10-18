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

compose.desktop {
    application {
        mainClass = "io.github.catlinux.keynexa.desktop.app.MainKt"
    }
}
