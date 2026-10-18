plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":platform:jvm"))
    implementation(project(":core:sync"))

    testImplementation(libs.junit)
}
