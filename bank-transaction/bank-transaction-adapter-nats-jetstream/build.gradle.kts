description = "Bank Transaction NATS JetStream Adapter Module"

dependencies {
    api(project(":bank-transaction:bank-transaction-domain"))
    api(project(":bank-transaction:bank-transaction-application"))
    api(project(":bank-transaction:bank-transaction-contract-proto"))
    api(project(":bank-transaction:bank-transaction-adapter-redis-stream"))
    api(project(":shared-kernel:connector-nats-jetstream"))
    api(project(":shared-kernel:connector-nats-jetstream-starter"))

    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.integration:spring-integration-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.integration:spring-integration-test")
}
