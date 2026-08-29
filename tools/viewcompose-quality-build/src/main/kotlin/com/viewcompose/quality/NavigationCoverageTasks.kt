package com.viewcompose.quality

import java.math.BigDecimal
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

/** Registers the accepted navigation critical-path coverage report and non-regression floors. */
internal fun Project.registerNavigationCoverageTasks(extension: ViewComposeQualityExtension) {
    pluginManager.apply("jacoco")

    val report = tasks.register<JacocoReport>("navigationCoverageReport") {
        group = "verification"
        description = "Report critical Navigation Core and Android host line/branch coverage."
        reports {
            xml.required.set(true)
            xml.outputLocation.set(extension.reportsDirectory.file("navigation-coverage/report.xml"))
            html.required.set(true)
            html.outputLocation.set(extension.reportsDirectory.dir("navigation-coverage/html"))
            csv.required.set(false)
        }
    }
    val verify = tasks.register("verifyNavigationCoverage") {
        group = "verification"
        description = "Verify accepted Navigation Core and Android host coverage floors."
        dependsOn(report)
    }
    tasks.matching { task -> task.name == "qaQuick" }.configureEach {
        dependsOn(verify)
    }

    val core = findProject(":viewcompose-navigation-core") ?: return
    val android = findProject(":viewcompose-navigation-android") ?: return
    listOf(core, android).forEach { target ->
        target.pluginManager.apply("jacoco")
        target.tasks.withType<Test>().configureEach {
            extensions.configure<JacocoTaskExtension> {
                isIncludeNoLocationClasses = true
                excludes = listOf("jdk.internal.*")
            }
        }
    }

    val coreClasses = files(
        core.layout.buildDirectory.dir("classes/kotlin/main").map { classes ->
            classes.asFileTree.matching {
                coreCoverageClassPatterns.forEach(::include)
                navigationCoverageGeneratedClassPatterns.forEach(::exclude)
            }
        },
    )
    val androidClasses = files(
        android.layout.buildDirectory.dir("tmp/kotlin-classes/debug").map { classes ->
            classes.asFileTree.matching {
                androidCoverageClassPatterns.forEach(::include)
                navigationCoverageGeneratedClassPatterns.forEach(::exclude)
            }
        },
    )
    val executionData = files(
        core.layout.buildDirectory.file("jacoco/test.exec"),
        android.layout.buildDirectory.file(
            "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
        ),
    )
    val testTasks = listOf(
        ":viewcompose-navigation-core:test",
        ":viewcompose-navigation-android:testDebugUnitTest",
    )

    report.configure {
        dependsOn(testTasks)
        sourceDirectories.from(
            core.layout.projectDirectory.dir("src/main/kotlin"),
            android.layout.projectDirectory.dir("src/main/java"),
        )
        classDirectories.from(coreClasses, androidClasses)
        executionData(executionData)
        doFirst {
            requireNavigationCoverageInputs(coreClasses, androidClasses, executionData)
        }
    }

    val coreVerification = tasks.register<JacocoCoverageVerification>(
        "verifyNavigationCoreCoverage",
    ) {
        group = "verification"
        description = "Verify scene, lifecycle, and reducer coverage floors."
        dependsOn(testTasks)
        classDirectories.from(coreClasses)
        executionData(executionData)
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal("0.80")
                }
                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal("0.70")
                }
            }
        }
        doFirst {
            requireNavigationCoverageInputs(coreClasses, androidClasses, executionData)
        }
        mustRunAfter(report)
    }
    val androidVerification = tasks.register<JacocoCoverageVerification>(
        "verifyNavigationAndroidCoverage",
    ) {
        group = "verification"
        description = "Verify host executor, ownership, session, retention, and Back coverage floors."
        dependsOn(testTasks)
        classDirectories.from(androidClasses)
        executionData(executionData)
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal("0.70")
                }
                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal("0.60")
                }
            }
        }
        doFirst {
            requireNavigationCoverageInputs(coreClasses, androidClasses, executionData)
        }
        mustRunAfter(report)
    }
    verify.configure {
        dependsOn(coreVerification, androidVerification)
    }
}

private fun requireNavigationCoverageInputs(
    coreClasses: FileCollection,
    androidClasses: FileCollection,
    executionData: FileCollection,
) {
    val missing = buildList {
        if (coreClasses.files.none { file -> file.isFile }) add("Navigation Core class bundle")
        if (androidClasses.files.none { file -> file.isFile }) add("Navigation Android class bundle")
        executionData.files.filterNot { file -> file.isFile }.forEach { file ->
            add("execution data ${file.invariantSeparatorsPath}")
        }
    }
    if (missing.isNotEmpty()) {
        throw GradleException(
            "Navigation coverage inputs are incomplete: ${missing.joinToString()}.",
        )
    }
}

private val coreCoverageClassPatterns = listOf(
    "com/viewcompose/navigation/core/NavExecutionPlan*",
    "com/viewcompose/navigation/core/NavLifecyclePlanner*",
    "com/viewcompose/navigation/core/NavPaneScene*",
    "com/viewcompose/navigation/core/NavScene*",
)

private val androidCoverageClassPatterns = listOf(
    "com/viewcompose/navigation/AndroidNavExecutionPlanExecutor*",
    "com/viewcompose/navigation/AndroidNavHostBackAdapter*",
    "com/viewcompose/navigation/NavDestinationSession*",
    "com/viewcompose/navigation/NavDestinationSessionStore*",
    "com/viewcompose/navigation/NavEntryOwner*",
    "com/viewcompose/navigation/NavGraphOwner*",
    "com/viewcompose/navigation/NavHostCoordinatorModel*",
    "com/viewcompose/navigation/NavHostDsl*",
    "com/viewcompose/navigation/NavHostRuntime*",
    "com/viewcompose/navigation/NavPresentationRetentionPolicy*",
    "com/viewcompose/navigation/TransactionalNavHostCoordinator*",
)

private val navigationCoverageGeneratedClassPatterns = listOf(
    "**/*\$WhenMappings*",
    "**/*\$DefaultImpls*",
    "**/*\$\$ExternalSynthetic*",
)
