description = "Bank Transaction REST Adapter Module"

plugins {
    id("io.spring.dependency-management")
}

dependencies {
    api(project(":bank-transaction:bank-transaction-application"))

    // Spring WebFlux
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
}
