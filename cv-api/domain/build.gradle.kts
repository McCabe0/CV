// Pure kotlin: the model and its rules. No framework may be added here. See decisions/architecture.md.
plugins {
    `java-library`
    // Fixtures every module's tests share, so a valid profile is defined once.
    `java-test-fixtures`
}
