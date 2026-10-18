plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:vault"))
    api(project(":core:sync"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(project(":core:kdbx"))
    testImplementation(project(":platform:jvm"))
}
