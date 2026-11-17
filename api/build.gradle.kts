import org.gradle.jvm.application.tasks.CreateStartScripts

plugins { application }

description = "Loopback-only bounded asynchronous CAE research API."

dependencies {
    implementation(project(":numerics-core"))
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.9"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation(libs.gson)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

application {
    mainClass.set("io.github.gbalazsfalvi.fuzzy.api.LabApplication")
    applicationDefaultJvmArgs = listOf("-Xms64m", "-Xmx512m")
}

tasks.named<JavaExec>("run") { workingDir(rootProject.projectDir) }

tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        // A JAR-by-JAR classpath can exceed CMD's 8191-character limit in long install paths.
        // Keep Gradle's quoted Java invocation and all other upstream launcher behavior intact.
        val assignment = Regex("""(?m)^set CLASSPATH=[^\r\n]*""")
        val generated = windowsScript.readText(Charsets.UTF_8)
        check(assignment.findAll(generated).count() == 1) {
            "Expected one Windows launcher classpath assignment."
        }
        windowsScript.writeText(
            assignment.replace(generated) { "set \"CLASSPATH=%APP_HOME%\\lib\\*\"" },
            Charsets.UTF_8,
        )
    }
}
