plugins {
    id("io.spring.dependency-management")
}

dependencies {
    api(project(":shared-kernel:connector-nats-jetstream"))

    // Spring Boot auto-configuration
    api("org.springframework.boot:spring-boot-autoconfigure")

    // Validation
    api("org.springframework.boot:spring-boot-starter-validation")

    // Spring integration
    api("org.springframework.integration:spring-integration-core")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:testcontainers:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:junit-jupiter:${property("testcontainersJunitVersion")}")
    testImplementation("org.awaitility:awaitility-kotlin")
}
