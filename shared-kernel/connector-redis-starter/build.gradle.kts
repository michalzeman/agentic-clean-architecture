dependencies {
    api(project(":shared-kernel:connector-redis"))

    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("org.springframework.integration:spring-integration-redis")
}
