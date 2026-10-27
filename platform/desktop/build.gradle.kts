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
    api(project(":platform:cloud"))
    api(project(":core:sync"))
    api(project(":core:update"))

    testImplementation(libs.junit)
}
