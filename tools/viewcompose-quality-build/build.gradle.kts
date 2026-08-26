plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

kotlin {
    jvmToolchain(17)
}

group = "com.viewcompose.quality"

dependencies {
    testImplementation(gradleTestKit())
    testImplementation("junit:junit:4.13.2")
}

val pullRequestImpactOutputDirectory = layout.buildDirectory.dir("reports/pull-request-impact")

tasks.register<JavaExec>("planPullRequestImpact") {
    group = "verification"
    description =
        "Classifies a repository diff without configuring the Android multi-project build."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.viewcompose.quality.PullRequestImpactCli")
    dependsOn(tasks.named("classes"))
    doFirst {
        val repository = providers.gradleProperty("viewComposeRepositoryRoot")
            .orElse(layout.projectDirectory.dir("../..").asFile.absolutePath)
            .get()
        val base = providers.gradleProperty("viewComposeBaseRevision")
            .orElse(providers.environmentVariable("VIEWCOMPOSE_IMPACT_BASE_REVISION"))
            .get()
        val head = providers.gradleProperty("viewComposeHeadRevision")
            .orElse(providers.environmentVariable("VIEWCOMPOSE_IMPACT_HEAD_REVISION"))
            .orElse("HEAD")
            .get()
        val event = providers.gradleProperty("viewComposeEventName")
            .orElse(providers.environmentVariable("VIEWCOMPOSE_IMPACT_EVENT_NAME"))
            .orElse("pull_request")
            .get()
        val forceFull = providers.gradleProperty("viewComposeForceFull")
            .orElse(providers.environmentVariable("VIEWCOMPOSE_FORCE_FULL"))
            .orElse("false")
            .get()
        val maxChangedFiles = providers.gradleProperty("viewComposeMaxChangedFiles")
            .orElse("300")
            .get()
        val output = pullRequestImpactOutputDirectory.get().asFile
        args(
            "--repository", repository,
            "--base", base,
            "--head", head,
            "--event", event,
            "--force-full", forceFull,
            "--max-changed-files", maxChangedFiles,
            "--json-output", output.resolve("plan.json").absolutePath,
            "--summary-output", output.resolve("summary.md").absolutePath,
            "--github-output", output.resolve("github-output.txt").absolutePath,
        )
    }
}

gradlePlugin {
    plugins {
        create("viewComposeQualityRoot") {
            id = "com.viewcompose.quality.root"
            implementationClass = "com.viewcompose.quality.ViewComposeQualityRootPlugin"
            displayName = "ViewCompose repository quality"
            description = "Provides compiled, testable ownership for ViewCompose quality gates."
        }
    }
}
