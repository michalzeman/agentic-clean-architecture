description = "Bank Account Redis Adapter Module"

plugins {
    id("io.spring.dependency-management")
}

dependencies {
    api(project(":bank-account:bank-account-application"))
    api(project(":shared-kernel:connector-redis"))

    // Spring Integration for MessageChannel
    implementation("org.springframework.integration:spring-integration-core")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
}
