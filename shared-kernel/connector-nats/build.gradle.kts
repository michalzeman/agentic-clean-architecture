plugins {
    id("io.spring.dependency-management")
}

dependencies {
    api(project(":shared-kernel:shared-domain"))

    // NATS Java client
    api("io.nats:jnats:${property("natsJavaClientVersion")}")

    // Spring context for configuration
    api("org.springframework:spring-context")

    // Spring Boot autoconfigure for @ConfigurationProperties
    api("org.springframework.boot:spring-boot-autoconfigure")

    // Logging
    api("org.slf4j:slf4j-api")

    // Jackson for JSON serialization
    api("tools.jackson.core:jackson-core")
    api("tools.jackson.core:jackson-databind")
    api("tools.jackson.module:jackson-module-kotlin")

    // Coroutines support
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.nats:jnats-server-runner:${property("natsJavaClientVersion")}")
}
