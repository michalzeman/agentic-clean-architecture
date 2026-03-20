plugins {
    id("io.spring.dependency-management")
}

dependencies {
    api(project(":shared-kernel:connector-nats"))

    // Spring integration for channel adapters
    api("org.springframework.integration:spring-integration-core")
    api("org.springframework:spring-messaging")

    // Spring context
    api("org.springframework:spring-context")

    // Coroutines support
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    api("io.projectreactor.kotlin:reactor-kotlin-extensions")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:testcontainers:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:junit-jupiter:${property("testcontainersVersion")}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    testImplementation("org.awaitility:awaitility-kotlin")
}
