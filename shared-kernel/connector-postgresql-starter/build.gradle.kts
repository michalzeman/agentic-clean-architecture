plugins {
    id("io.spring.dependency-management")
}

dependencies {
    api(project(":shared-kernel:connector-postgresql"))

    // Spring Boot auto-configuration
    api("org.springframework.boot:spring-boot-autoconfigure")

    // Spring Integration support
    api("org.springframework.integration:spring-integration-core")

    // Liquibase for database migrations (starter includes auto-configuration extracted in Spring Boot 4.0)
    api("org.springframework.boot:spring-boot-starter-liquibase")

    // Jackson for JSON serialization support
    api("tools.jackson.core:jackson-databind")
    api("tools.jackson.module:jackson-module-kotlin")
}
