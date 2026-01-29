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

    api(libs.redisson)

    api("com.fasterxml.jackson.core:jackson-core")
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jdk8")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    api("com.fasterxml.jackson.module:jackson-module-kotlin")

    testImplementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
}
