plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "cv-api"

// Hexagonal, see decisions/architecture.md. Each module can only see the ones inside it.
include("domain", "application", "adapters", "app")
