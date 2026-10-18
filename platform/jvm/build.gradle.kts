plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:sync"))
    api(project(":core:vault"))

    testImplementation(libs.junit)
}
