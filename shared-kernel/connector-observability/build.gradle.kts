plugins {
    id("io.spring.dependency-management")
}

dependencies {
    api(project(":shared-kernel:shared-domain"))
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("org.springframework.boot:spring-boot-starter-actuator")
    api("io.micrometer:micrometer-registry-prometheus")
    api("org.springframework.integration:spring-integration-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
