plugins {
    id("org.springframework.boot")
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":bank-account:bank-account-domain"))
    implementation(project(":bank-account:bank-account-application"))
    implementation(project(":bank-account:bank-account-adapter-redis"))
    implementation(project(":bank-account:bank-account-adapter-redis-stream"))
    implementation(project(":bank-account:bank-account-adapter-rest"))
    implementation(project(":shared-kernel:connector-redis-starter"))
    implementation(project(":shared-kernel:connector-postgresql-starter"))

    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    implementation("org.springframework.boot:spring-boot-starter-integration")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")

    // PostgreSQL driver for message channel persistence
    implementation("org.postgresql:postgresql")

    // Spring Cloud, Integration
    implementation("org.springframework.cloud:spring-cloud-starter")
    implementation("org.springframework.integration:spring-integration-core")
    implementation("org.springframework.integration:spring-integration-redis")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.integration:spring-integration-test")
}
