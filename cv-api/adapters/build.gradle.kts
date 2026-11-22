// Spring lives here: web, persistence, messaging, the LLMs and the job boards. Each adapter implements a
// port from :application. See decisions/architecture.md.
plugins {
    `java-library`
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("io.spring.dependency-management")
}

kotlin {
    compilerOptions {
        // So bean validation sees constraints on list elements, eg List<@NotBlank String>. Without it kotlin
        // drops them and a blank skill gets past the web layer.
        freeCompilerArgs.add("-Xemit-jvm-type-annotations")
    }
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    api(project(":application"))

    api("org.springframework.boot:spring-boot-starter-webmvc")
    api("org.springframework.boot:spring-boot-starter-security")
    api("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0")

    // The LLMs behind the CV agents. Claude through its official sdk; Gemini over http with spring's
    // RestClient. See decisions/agents.md.
    implementation("com.anthropic:anthropic-java:3.3.0")

    // The outbox relay and the cv-worker's listener. See decisions/messaging.md.
    api("org.springframework.boot:spring-boot-starter-kafka")

    // The persistence adapter owns the schema, so its migrations live beside its entities.
    api("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation(testFixtures(project(":domain")))
    testImplementation(testFixtures(project(":application")))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.4.0")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    // Plays each LLM provider's api in the contract tests. Standalone, so its jetty cannot clash.
    testImplementation("org.wiremock:wiremock-standalone:3.13.2")
}
