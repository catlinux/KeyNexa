plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":platform:desktop"))
    implementation(libs.jna)
    implementation(libs.jna.platform)

    testImplementation(libs.junit)
}

// Prueba manual de Windows Hello (necesita el PIN del usuario): no forma parte de `check`.
tasks.register<JavaExec>("helloProbe") {
    group = "verification"
    description = "Prueba manual de Windows Hello con el PIN del usuario (ADR-0014, paso 0)."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.catlinux.keynexa.windows.WindowsHelloProbeKt")
}
