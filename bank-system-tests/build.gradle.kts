plugins {
    id("org.springframework.boot")
}

dependencies {
    // Spring Boot Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // WebClient for HTTP tests
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")

    testImplementation("org.springframework.integration:spring-integration-core")
    testImplementation("org.springframework.integration:spring-integration-redis")

    testImplementation(project(":shared-kernel:connector-redis-starter"))

    testImplementation(project(":bank-account:bank-account-contract-proto"))
    testImplementation(project(":bank-transaction:bank-transaction-contract-proto"))
}

// Disable bootJar as this is a test-only module
tasks.bootJar {
    enabled = false
}

// Configure the systemTest task from parent build.gradle
tasks.named<Test>("systemTest") {
    description = "Runs system integration tests against external bank services"
    dependsOn(":dockerComposeUp")
    finalizedBy(":tearDownDockerCompose")
    useJUnitPlatform {
        includeTags("systemChecks")
    }
}
