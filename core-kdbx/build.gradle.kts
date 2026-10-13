plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core-vault"))
    implementation(project(":kotpass"))

    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    maxHeapSize = providers.environmentVariable("KEYNEXA_TEST_HEAP").getOrElse("2g")
}
