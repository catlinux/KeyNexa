plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:vault"))
    implementation(project(":kotpass"))

    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    maxHeapSize = providers.environmentVariable("KEYNEXA_TEST_HEAP").getOrElse("2g")
}

// Writes the test runtime classpath so the Jazzer CLI (a CI binary, not a Gradle dependency) can find the target.
tasks.register("writeFuzzClasspath") {
    group = "verification"
    description = "Writes the test runtime classpath to build/fuzz-classpath.txt for the Jazzer CLI."
    dependsOn(tasks.named("testClasses"))
    val testRuntimeClasspath = sourceSets.test.get().runtimeClasspath
    val outputFile = layout.buildDirectory.file("fuzz-classpath.txt")
    inputs.files(testRuntimeClasspath)
    outputs.file(outputFile)
    doLast {
        outputFile.get().asFile.writeText(testRuntimeClasspath.asPath)
    }
}
