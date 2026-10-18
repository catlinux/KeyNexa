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
