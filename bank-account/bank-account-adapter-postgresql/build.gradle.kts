description = "Bank Account PostgreSQL Adapter Module"

plugins {
    id("io.spring.dependency-management")
}

dependencies {
    api(project(":bank-account:bank-account-application"))

    // Spring Data JDBC for aggregate persistence
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")

    // Spring Integration for MessageChannel
    implementation("org.springframework.integration:spring-integration-core")

    // PostgreSQL driver for JSONB PGobject support
    implementation("org.postgresql:postgresql")

    // Jackson for JSONB serialization
    implementation("tools.jackson.module:jackson-module-kotlin")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    // Test: Spring Data JDBC slice test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jdbc")

    // Test: Testcontainers
    testImplementation("org.testcontainers:testcontainers:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:postgresql:${property("testcontainersJunitVersion")}")
    testImplementation("org.testcontainers:junit-jupiter:${property("testcontainersJunitVersion")}")

    // Test: Liquibase + V1 classpath resource from connector-postgresql-starter
    testImplementation(project(":shared-kernel:connector-postgresql-starter"))
}
