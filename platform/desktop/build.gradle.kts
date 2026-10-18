plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:vault"))
    api(project(":core:session"))
    api(project(":platform:jvm"))

    testImplementation(libs.junit)
}
