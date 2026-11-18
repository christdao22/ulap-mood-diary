import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The Ktor server (README §5, technical-plan.md §4): routes → service → DAL, PostgreSQL through
// HikariCP, Flyway migrations. It shares models, rules and DTOs with the app through :shared.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    // Adds `run` and `buildFatJar` (build/libs/server-all.jar, used by server/Dockerfile).
    alias(libs.plugins.ktor)
}

application {
    mainClass = "app.ulap.server.ApplicationKt"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        // CI passes -PwarningsAsErrors=true, like the Android modules (ulap-reference.md §4.4).
        allWarningsAsErrors = providers.gradleProperty("warningsAsErrors").map { it.toBoolean() }.orElse(false)
    }
}

// `./gradlew :server:run` reads the repo's .env (README §6.2), so the server starts with the same
// variables Docker would pass it. Lines are KEY=value; blank lines and # comments are skipped.
// A missing .env is fine: the server then names every required variable that's missing.
fun envFile(path: String): Map<String, String> = providers.fileContents(rootProject.layout.projectDirectory.file(path))
    .asText
    .map { text ->
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
            .associate { line -> line.substringBefore("=").trim() to line.substringAfter("=").trim() }
    }
    .getOrElse(emptyMap())

val dotEnv: Map<String, String> = envFile(".env")

tasks.named<JavaExec>("run") {
    environment(dotEnv)
    // Ktor development mode (auto-reload with `run -t`) only when the .env says development.
    if (dotEnv["APP_ENV"] == "development") {
        jvmArgs("-Dio.ktor.development=true")
    }
}

// Seeds or removes the sync load-test accounts (docs/runbooks/load-test.md):
//   ./gradlew :server:loadTestUsers --args="seed --users 1000"
//   ./gradlew :server:loadTestUsers --args="cleanup"
// It reads .env, or the env file of the server under test given as -PloadTestEnv=<path from the
// repo root> (staging). The tool lives in the test source set, so it never ships in the server jar.
tasks.register<JavaExec>("loadTestUsers") {
    group = "verification"
    description = "Seeds or removes the accounts the k6 sync load test runs as."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "app.ulap.server.loadtest.LoadTestMainKt"
    workingDir = layout.projectDirectory.asFile
    environment(providers.gradleProperty("loadTestEnv").map(::envFile).getOrElse(dotEnv))
}

tasks.withType<Test>().configureEach {
    // Testcontainers starts PostgreSQL in Docker (README §11); show why a test failed.
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

dependencies {
    implementation(projects.shared)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.call.id)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.auth)

    // Auth (api-spec.md §3, §6): signed access tokens, Argon2id password hashes, Google ID token
    // checks and the verification and reset emails.
    implementation(libs.java.jwt)
    implementation(libs.argon2.jvm)
    implementation(libs.google.api.client)
    implementation(libs.jakarta.mail)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.hikari)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.logback.classic)
    // Crash reports (D11), only when SENTRY_DSN is set. CrashReporting scrubs every event.
    implementation(libs.sentry)

    testImplementation(libs.ktor.server.test.host)
    // The test client sends and reads the shared DTOs as JSON.
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.testcontainers.postgresql)
}
