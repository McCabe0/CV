import info.solidsoft.gradle.pitest.PitestPluginExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    kotlin("jvm") version "2.4.21" apply false
    kotlin("plugin.spring") version "2.4.21" apply false
    kotlin("plugin.jpa") version "2.4.21" apply false
    id("org.springframework.boot") version "4.2.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0" apply false
    id("info.solidsoft.pitest") version "1.19.0" apply false
    // Merges each module's mutation report into one: `./gradlew pitest pitestReportAggregate`.
    id("info.solidsoft.pitest.aggregator") version "1.19.0"
}

// For pitest's aggregator, the only thing the root project resolves.
repositories {
    mavenCentral()
}

// The rules every module keeps, so `./gradlew check` holds them all. See decisions/architecture.md.
subprojects {
    group = "com.skill2career"

    // Spring boot 4.1.2 brings tomcat 11.0.27, which has two critical and six high vulnerabilities fixed
    // in 11.0.29: CVE-2026-116561, -118028, -118233, -116600, -116830, -118436, -117517 and -106012. Set
    // here so both modules that import boot's bom, adapters and app, take it. Goes once boot brings
    // 11.0.29 or later. See decisions/cve.md.
    extra["tomcat.version"] = "11.0.29"

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "jacoco")

    repositories {
        mavenCentral()
    }

    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(25)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_25)
            freeCompilerArgs.addAll("-Xjsr305=strict")
        }
    }

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.8.0")
    }

    extensions.configure<JacocoPluginExtension> {
        toolVersion = "0.8.16"
    }

    dependencies {
        "testImplementation"("org.jetbrains.kotlin:kotlin-test-junit5")
        "testImplementation"("org.assertj:assertj-core:3.27.7")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        reports {
            xml.required.set(true)
            csv.required.set(true)
        }
    }

    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn(tasks.named("test"))
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    minimum = "0.9".toBigDecimal()
                }
                limit {
                    counter = "BRANCH"
                    minimum = "0.9".toBigDecimal()
                }
            }
        }
    }

    tasks.named("check") {
        dependsOn(tasks.named("jacocoTestCoverageVerification"))
    }
}

// Mutation testing, weekly and report only, by .github/workflows/mutation.yml. Not part of check. See
// decisions/mutation.md. Each module mutates only its own code, with its own tests, and the root merges
// the reports. App is left out: it is the wiring, which the smoke tests check, not its own tests.
val mutated = mapOf("domain" to "domain", "application" to "application", "adapters" to "adapter")
mutated.forEach { (module, pkg) ->
    project(":$module") {
        apply(plugin = "info.solidsoft.pitest")

        extensions.configure<PitestPluginExtension> {
            pitestVersion.set("1.30.2")
            junit5PluginVersion.set("1.2.3")
            targetClasses.set(setOf("com.skill2career.$pkg.*"))
            targetTests.set(setOf("com.skill2career.*"))
            // Spring configuration runs once, when a test's context starts, and spring caches the context
            // across tests, and across the mutants a minion runs. So pitest credits it to one test per
            // class, and runs each mutant against a context built before it: no test can kill one. Its
            // rules are checked by the resource tests' 401s and 403s, the smoke tests and ZAP.
            excludedClasses.set(
                setOf(
                    "com.skill2career.adapter.web.security.SecurityConfiguration*",
                    "com.skill2career.adapter.llm.LlmClients*",
                ),
            )
            // Code kotlin generates, that no test can or should kill: its null checks, its coroutines'
            // result handling, and a data class's methods. And logging, which pitest leaves alone by
            // default, but only while this list is not set.
            avoidCallsTo.set(
                setOf(
                    "kotlin.jvm.internal",
                    "kotlin.ResultKt",
                    "org.slf4j",
                    "java.util.logging",
                    "org.apache.commons.logging",
                ),
            )
            excludedMethods.set(setOf("equals", "hashCode", "toString", "copy", "component*"))
            threads.set(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
            outputFormats.set(setOf("HTML", "XML"))
            // The aggregator needs each module's line coverage, and the reports at a fixed path.
            exportLineCoverage.set(true)
            timestampedReports.set(false)
        }
    }
}
