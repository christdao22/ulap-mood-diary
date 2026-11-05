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
val dotEnv: Map<String, String> = providers.fileContents(rootProject.layout.projectDirectory.file(".env"))
    .asText
    .map { text ->
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
            .associate { line -> line.substringBefore("=").trim() to line.substringAfter("=").trim() }
    }
    .getOrElse(emptyMap())

tasks.named<JavaExec>("run") {
    environment(dotEnv)
    // Ktor development mode (auto-reload with `run -t`) only when the .env says development.
    if (dotEnv["APP_ENV"] == "development") {
        jvmArgs("-Dio.ktor.development=true")
    }
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
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.hikari)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.logback.classic)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.testcontainers.postgresql)
}
