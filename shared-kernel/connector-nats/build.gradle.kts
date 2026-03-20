plugins {
    id("io.spring.dependency-management")
}

dependencies {
    api(project(":shared-kernel:shared-domain"))

    // NATS Java client
    api("io.nats:jnats:${property("natsJavaClientVersion")}")

    // Spring context for configuration
    api("org.springframework:spring-context")

    // Logging
    api("org.slf4j:slf4j-api")

    // Jackson for JSON serialization
    api("com.fasterxml.jackson.core:jackson-core")
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    api("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Coroutines support
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.nats:jnats-server-runner:${property("natsJavaClientVersion")}")
}
