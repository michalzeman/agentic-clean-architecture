dependencies {
    api(project(":shared-kernel:connector-redis"))
    api("org.springframework.boot:spring-boot-health")
    api(libs.redisson.spring.starter)

    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("org.springframework.integration:spring-integration-redis")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-redis")
}
