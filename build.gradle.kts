import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    base
    alias(libs.plugins.spotless) apply false
}

val npmExecutable = if (System.getProperty("os.name").startsWith("Windows"))
    listOf("cmd.exe", "/c", "npm.cmd") else listOf("npm")

val webInstall by tasks.registering(Exec::class) {
    group = "build"
    description = "Installs the exact locked workbench dependencies."
    workingDir("web")
    commandLine(npmExecutable + listOf("ci", "--no-audit", "--no-fund"))
    inputs.files("web/package.json", "web/package-lock.json")
    outputs.dir("web/node_modules")
}

val webBuild by tasks.registering(Exec::class) {
    group = "build"
    description = "Type checks and builds the local React workbench."
    dependsOn(webInstall)
    workingDir("web")
    commandLine(npmExecutable + listOf("run", "build"))
    inputs.files(fileTree("web") {
        include("src/**", "public/**", "index.html", "*.json", "vite.config.ts")
        exclude("node_modules/**", "dist/**", "artifacts/**")
    })
    outputs.dir("web/dist")
}

val webTest by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs workbench behavioral unit tests."
    dependsOn(webInstall)
    workingDir("web")
    commandLine(npmExecutable + listOf("test"))
}

allprojects {
    group = "io.github.gbalazsfalvi.fuzzy"
    version = "0.1.0"
    repositories { mavenCentral() }
}

val junitBom = libs.junit.bom
val junitJupiter = libs.junit.jupiter
val junitLauncher = libs.junit.launcher
val formatterVersion = libs.versions.googleJavaFormat.get()
val jacocoVersion = libs.versions.jacoco.get()

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "jacoco")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
        withSourcesJar()
        withJavadocJar()
    }

    extensions.configure<SpotlessExtension> {
        java {
            googleJavaFormat(formatterVersion)
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    extensions.configure<JacocoPluginExtension> { toolVersion = jacocoVersion }

    dependencies {
        add("testImplementation", platform(junitBom))
        add("testImplementation", junitJupiter)
        add("testRuntimeOnly", junitLauncher)
    }
    dependencyLocking { lockAllConfigurations() }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }
    tasks.withType<Javadoc>().configureEach {
        options.encoding = "UTF-8"
        (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:all", true)
        isFailOnError = true
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        maxParallelForks = 1
        maxHeapSize = "512m"
        testLogging { events("failed", "skipped") }
    }
    tasks.withType<JacocoReport>().configureEach {
        dependsOn(tasks.named("test"))
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
    tasks.named("check") {
        dependsOn("spotlessCheck", "javadoc", "jacocoTestReport")
    }
}

tasks.named("check") { dependsOn(":numerics-core:check", ":experiments:check", ":api:check", webTest) }
tasks.named("assemble") { dependsOn(":numerics-core:assemble", ":experiments:assemble", ":api:assemble") }

tasks.register("foundationDemo") {
    group = "application"
    description = "Runs the numerical foundation checks and writes a local JSON report."
    dependsOn(":experiments:run")
}

tasks.register("caeDemo") {
    group = "application"
    description = "Runs milestone 02 CAE examples and exports replayable evidence."
    dependsOn(":experiments:caeDemo")
}

tasks.register("verifyCaeDemo") {
    group = "verification"
    description = "Verifies saved CAE evidence without regenerating or overwriting it."
    dependsOn(":experiments:verifyCaeDemo")
}

tasks.register("comparisonDemo") {
    group = "application"
    description = "Runs the versioned exploratory comparison and exports all outcomes."
    dependsOn(":experiments:comparisonDemo")
}

tasks.register("learningDemo") {
    group = "application"
    description = "Generates isolated train/validation split labels and their numerical work evidence."
    dependsOn(":experiments:learningDemo")
}

tasks.register("validationDemo") {
    group = "application"
    description = "Compares all candidate policies on the isolated validation cohort."
    dependsOn(":experiments:validationDemo")
}

tasks.register("evaluationDemo") {
    group = "application"
    description = "Runs the frozen held out evaluation."
    dependsOn(":experiments:evaluationDemo")
}

listOf("modelParity", "freezeEvaluation", "verifyValidation", "verifyEvaluation").forEach { name ->
    tasks.register(name) {
        group = "verification"
        dependsOn(":experiments:$name")
    }
}

project(":api").tasks.named<ProcessResources>("processResources") {
    dependsOn(webBuild)
    from(layout.projectDirectory.dir("web/dist")) { into("static") }
}

tasks.register("verifyComparison") {
    group = "verification"
    description = "Replays saved comparison geometry and checks the complete declared cohort."
    dependsOn(":experiments:verifyComparison")
}
