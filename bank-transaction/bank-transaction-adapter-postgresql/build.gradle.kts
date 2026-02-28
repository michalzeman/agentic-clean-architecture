description = "Bank Transaction PostgreSQL Adapter Module"

plugins {
    id("io.spring.dependency-management")
}

dependencies {
    api(project(":bank-transaction:bank-transaction-application"))

    // Spring Data JDBC for aggregate persistence
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")

    // Spring Integration for MessageChannel
    implementation("org.springframework.integration:spring-integration-core")

    // PostgreSQL driver for JSONB PGobject support
    implementation("org.postgresql:postgresql")

    // Jackson for JSONB serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
}
