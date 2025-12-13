description = "Bank Transaction Redis Adapter Module"

plugins {
    id("io.spring.dependency-management")
}

dependencies {
    api(project(":bank-transaction:bank-transaction-application"))
    api(project(":shared-kernel:connector-redis"))

    // Spring Integration for MessageChannel
    implementation("org.springframework.integration:spring-integration-core")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
}
