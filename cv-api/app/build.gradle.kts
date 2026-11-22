import org.springframework.boot.gradle.tasks.bundling.BootJar

// The runnable: spring boot's main, the wiring, security, and the integration tests against real
// containers. See decisions/architecture.md.
plugins {
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("info.solidsoft.pitest")
}

dependencies {
    implementation(project(":adapters"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")

    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    // The release built for the opentelemetry sdk that spring boot manages. See decisions/observability.md.
    implementation("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.34.0-alpha")
    implementation("net.ttddyy.observation:datasource-micrometer-spring-boot:2.3.1")

    testImplementation(testFixtures(project(":domain")))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-webtestclient")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("com.github.dasniko:testcontainers-keycloak:4.4.0")
    testImplementation("org.testcontainers:testcontainers-kafka")
    // Plays gemini in the cv pipeline test.
    testImplementation("org.wiremock:wiremock-standalone:3.13.2")
}

// Mutation testing, weekly and report only. Not part of check. See decisions/workflow.md.
pitest {
    pitestVersion.set("1.30.2")
    junit5PluginVersion.set("1.2.3")
    targetClasses.set(setOf("com.skill2career.*"))
    avoidCallsTo.set(setOf("kotlin.jvm.internal", "kotlin.ResultKt"))
    excludedMethods.set(setOf("equals", "hashCode", "toString", "copy", "component*"))
    outputFormats.set(setOf("HTML", "XML"))
    timestampedReports.set(false)
}

tasks.getByName<BootJar>("bootJar") {
    archiveFileName.set("cv-api.jar")
    mainClass.set("com.skill2career.app.CvApiKt")
}

tasks.getByName<Jar>("jar") {
    enabled = false
}
