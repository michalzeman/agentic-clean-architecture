description = "Bank Account NATS JetStream Adapter Module"

dependencies {
    api(project(":bank-account:bank-account-domain"))
    api(project(":bank-account:bank-account-application"))
    api(project(":bank-account:bank-account-contract-proto"))
    api(project(":bank-transaction:bank-transaction-contract-proto"))
    api(project(":shared-kernel:connector-nats-jetstream"))
    api(project(":shared-kernel:connector-nats-jetstream-starter"))

    // Spring Boot for configuration properties
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    // Spring Integration
    implementation("org.springframework.integration:spring-integration-core")
    implementation("org.springframework.integration:spring-integration-jdbc")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.integration:spring-integration-test")
    testImplementation("org.testcontainers:testcontainers:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:junit-jupiter:${property("testcontainersJunitVersion")}")
    testImplementation("org.awaitility:awaitility-kotlin")
}
