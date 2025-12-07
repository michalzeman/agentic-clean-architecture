description = "Bank Account Application Module"

dependencies {
    api(project(":bank-account:bank-account-domain"))
    api(project(":shared-kernel:shared-domain"))

    // Spring context for @Component
    implementation("org.springframework:spring-context")

    // Spring Integration for message channels
    implementation("org.springframework.integration:spring-integration-core")
    implementation("org.springframework.integration:spring-integration-redis")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
}
