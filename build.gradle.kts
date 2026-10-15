import com.github.jk1.license.render.JsonReportRenderer
import com.github.jk1.license.render.ReportRenderer
import com.github.jk1.license.render.TextReportRenderer
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.artifacts.dsl.LockMode
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.license.report)
}

licenseReport {
    projects = (listOf(project) + project.subprojects).toTypedArray()
    configurations = arrayOf("runtimeClasspath", "releaseRuntimeClasspath")
    renderers = arrayOf<ReportRenderer>(JsonReportRenderer(), TextReportRenderer())
}

subprojects {
    val lockedConfigurations =
        setOf(
            "runtimeClasspath",
            "releaseRuntimeClasspath",
            "offlineReleaseRuntimeClasspath",
            "cloudReleaseRuntimeClasspath",
        )
    configurations.configureEach {
        // The app ships two release flavors, so its released configuration names carry the flavor.
        if (name in lockedConfigurations) {
            resolutionStrategy.activateDependencyLocking()
        }
    }

    dependencyLocking {
        lockMode = LockMode.STRICT
    }

    val resolveLockedConfigurations = tasks.register("resolveLockedConfigurations") {
        group = "verification"
        description = "Resolves the locked runtime configurations so their lock state is written and checked."
        doLast {
            configurations
                .filter { it.name in lockedConfigurations }
                // Resolve dependencies without choosing artifacts: a plain resolve() cannot tell the artifact
                // variants of a flavored Android library apart, but dependency locking only needs the graph.
                .forEach { it.incoming.resolutionResult.allComponents }
        }
    }

    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(resolveLockedConfigurations)
    }

    // third_party/ keeps upstream code untouched apart from the changes listed in its README.
    if (path != ":kotpass") {
        pluginManager.apply("io.gitlab.arturbosch.detekt")
        pluginManager.apply("org.jlleitschuh.gradle.ktlint")

        configure<KtlintExtension> {
            android.set(true)
        }

        configure<DetektExtension> {
            buildUponDefaultConfig = true
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        }

        tasks.withType<Detekt>().configureEach {
            setSource(files("src/main/kotlin", "src/test/kotlin", "src/main/java", "src/test/java"))
        }
    }
}
