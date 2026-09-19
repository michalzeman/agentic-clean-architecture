plugins {
    id("io.spring.dependency-management")
}

dependencies {
    api(project(":shared-kernel:connector-nats"))

    // W3C traceparent format/restore helpers used by the consumer
    api(project(":shared-kernel:connector-observability"))

    // Spring integration for channel adapters
    api("org.springframework.integration:spring-integration-core")
    api("org.springframework:spring-messaging")

    // Spring context
    api("org.springframework:spring-context")

    // Spring Boot health for health indicator
    api("org.springframework.boot:spring-boot-health")

    // Coroutines support
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    api("io.projectreactor.kotlin:reactor-kotlin-extensions")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:testcontainers:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:junit-jupiter:${property("testcontainersJunitVersion")}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    testImplementation("org.awaitility:awaitility-kotlin")
    // SimpleTracer test kit; version managed by micrometer-tracing-bom via the Boot BOM
    testImplementation("io.micrometer:micrometer-tracing-test")
    // Shared SimpleW3CPropagator trace-context test fixture
    testImplementation(testFixtures(project(":shared-kernel:connector-observability")))
}
