plugins {
    id("io.spring.dependency-management")
}

dependencies {
    api(project(":shared-kernel:connector-postgresql"))

    // Spring Boot auto-configuration
    api("org.springframework.boot:spring-boot-autoconfigure")

    // Spring Integration support
    api("org.springframework.integration:spring-integration-core")

    // Liquibase for database migrations
    api("org.liquibase:liquibase-core")

    // Jackson for JSON serialization support
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.module:jackson-module-kotlin")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
}
