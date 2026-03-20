plugins {
    id("io.spring.dependency-management")
}

dependencies {
    api(project(":shared-kernel:connector-nats-jetstream"))

    // Spring Boot auto-configuration
    api("org.springframework.boot:spring-boot-autoconfigure")

    // Spring integration
    api("org.springframework.integration:spring-integration-core")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.nats:jnats-server-runner:${property("natsJavaClientVersion")}")
}
