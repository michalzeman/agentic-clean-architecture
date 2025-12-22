description = "Bank Transaction Redis Stream Adapter Module"

dependencies {
    api(project(":bank-transaction:bank-transaction-application"))
    api(project(":bank-transaction:bank-transaction-contract-proto"))
    api(project(":shared-kernel:connector-redis"))

    // Spring Boot for configuration properties
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    // Spring Integration
    implementation("org.springframework.integration:spring-integration-core")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
}
