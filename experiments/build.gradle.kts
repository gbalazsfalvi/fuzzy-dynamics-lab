plugins { application }

description = "Local numerical foundation and CAE demonstrations with replayable evidence."

dependencies {
    implementation(project(":numerics-core"))
    implementation(libs.gson)
}

application {
    mainClass.set("io.github.gbalazsfalvi.fuzzy.experiments.FoundationDemo")
    applicationDefaultJvmArgs = listOf("-Xms64m", "-Xmx512m")
}

tasks.named<JavaExec>("run") {
    workingDir(rootProject.projectDir)
    args("--output", "artifacts/foundation-demo.json")
}

val toolchains = extensions.getByType<JavaToolchainService>()
val demoRuntime = sourceSets.main.get().runtimeClasspath

tasks.register<JavaExec>("learningDemo") {
    group = "application"
    description = "Exports bounded signed split labels from train/validation problems only."
    mainClass.set("io.github.gbalazsfalvi.fuzzy.experiments.LearningDemo")
    javaLauncher.set(toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    classpath = demoRuntime
    workingDir(rootProject.projectDir)
    maxHeapSize = "512m"
}

tasks.register<JavaExec>("validationDemo") {
    group = "application"
    description = "Runs all candidates on validation base problems only."
    mainClass.set("io.github.gbalazsfalvi.fuzzy.experiments.ComparisonDemo")
    javaLauncher.set(toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    classpath = demoRuntime
    workingDir(rootProject.projectDir)
    maxHeapSize = "512m"
    args("--protocol", "research/protocols/validation-v1.json", "--output", "artifacts/validation")
}

tasks.register<JavaExec>("evaluationDemo") {
    group = "application"
    description = "Executes the frozen held out comparison with the predeclared timing cohort."
    mainClass.set("io.github.gbalazsfalvi.fuzzy.experiments.ComparisonDemo")
    javaLauncher.set(toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    classpath = demoRuntime
    workingDir(rootProject.projectDir)
    maxHeapSize = "512m"
    args("--protocol", "research/protocols/evaluation-locked-v1.json", "--output", "artifacts/evaluation")
}

listOf("modelParity" to "--parity", "freezeEvaluation" to "--freeze",
    "verifyValidation" to "--verify-validation", "verifyEvaluation" to "--verify-evaluation").forEach { (name, action) ->
    tasks.register<JavaExec>(name) {
        group = "verification"
        description = "Checks frozen model, source, protocol, or saved evaluation evidence."
        mainClass.set("io.github.gbalazsfalvi.fuzzy.experiments.EvaluationControl")
        javaLauncher.set(toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
        classpath = demoRuntime
        workingDir(rootProject.projectDir)
        maxHeapSize = "512m"
        args(action)
    }
}

tasks.register<JavaExec>("caeDemo") {
    group = "application"
    description = "Writes the local CAE JSON evidence and Markdown summary."
    mainClass.set("io.github.gbalazsfalvi.fuzzy.experiments.CaeDemo")
    javaLauncher.set(toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    classpath = demoRuntime
    workingDir(rootProject.projectDir)
    maxHeapSize = "512m"
    args("--output", "artifacts/cae-demo.json")
}

tasks.register<JavaExec>("verifyCaeDemo") {
    group = "verification"
    description = "Reloads and verifies the existing CAE JSON; does not generate it."
    mainClass.set("io.github.gbalazsfalvi.fuzzy.experiments.CaeDemo")
    javaLauncher.set(toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    classpath = demoRuntime
    workingDir(rootProject.projectDir)
    maxHeapSize = "512m"
    args("--verify", "artifacts/cae-demo.json")
    mustRunAfter("caeDemo")
}

tasks.register<JavaExec>("comparisonDemo") {
    group = "application"
    description = "Runs the milestone 03 paired pilot and writes local JSON and CSV evidence."
    mainClass.set("io.github.gbalazsfalvi.fuzzy.experiments.ComparisonDemo")
    javaLauncher.set(toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    classpath = demoRuntime
    workingDir(rootProject.projectDir)
    maxHeapSize = "512m"
    dependsOn(":numerics-core:check", ":experiments:check")
    mustRunAfter("run", "caeDemo", "verifyCaeDemo")
}

tasks.register<JavaExec>("verifyComparison") {
    group = "verification"
    description = "Verifies saved comparison inputs and geometry without replacing observations."
    mainClass.set("io.github.gbalazsfalvi.fuzzy.experiments.ComparisonDemo")
    javaLauncher.set(toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    classpath = demoRuntime
    workingDir(rootProject.projectDir)
    maxHeapSize = "512m"
    args("--verify", "artifacts/comparison")
    mustRunAfter("comparisonDemo")
}
