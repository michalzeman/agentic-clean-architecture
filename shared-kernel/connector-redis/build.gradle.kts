plugins {
    id("io.spring.dependency-management")
}

dependencies {
    api(project(":shared-kernel:shared-domain"))

    // spring context
    api("org.springframework:spring-context")

    // spring integration core
    api("org.springframework.integration:spring-integration-core")
    api("org.springframework.integration:spring-integration-redis")

    // https://mvnrepository.com/artifact/org.springframework/spring-tx
    implementation("org.springframework:spring-tx")

    // spring data redis
    api("org.springframework.data:spring-data-redis")
    api("org.springframework:spring-messaging")

    // lettuce redis driver
    api("io.lettuce:lettuce-core")

    // connection pooling for lettuce
    api("org.apache.commons:commons-pool2:2.12.0")

    api("org.springframework.boot:spring-boot-health")

    api(libs.redisson)

    api("tools.jackson.core:jackson-core")
    api("tools.jackson.core:jackson-databind")
    api("tools.jackson.module:jackson-module-kotlin")

    testImplementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
}
