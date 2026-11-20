// Use cases and the ports they need. Pure kotlin: it sees the domain and nothing else, so no adapter or
// framework can leak in. See decisions/architecture.md.
plugins {
    `java-library`
    // Hand written fakes of the ports, shared with the adapters' and app's tests.
    `java-test-fixtures`
}

dependencies {
    api(project(":domain"))

    testImplementation(testFixtures(project(":domain")))
    testFixturesImplementation(project(":domain"))
}
