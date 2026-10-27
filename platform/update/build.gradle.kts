plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:update"))
    implementation(project(":platform:jvm"))

    testImplementation(libs.junit)
}
