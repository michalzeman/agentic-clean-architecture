import com.google.protobuf.gradle.id


plugins {
    id("org.springframework.boot")
    id("com.google.protobuf")
    kotlin("jvm")
    kotlin("plugin.spring")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${property("protobufProtocVersion")}"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${property("protocGenGrpcJavaVersion")}"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
            }
        }
    }
}

dependencies {
    implementation(project(":shared-kernel:connector-redis"))
    implementation(project(":shared-kernel:connector-redis-starter"))

    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    implementation("org.springframework.boot:spring-boot-starter-integration")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Spring Cloud, Integration, and Kafka
    implementation("org.springframework.cloud:spring-cloud-starter")
    implementation("org.springframework.integration:spring-integration-core")
    implementation("org.springframework.integration:spring-integration-redis")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.modulith:spring-modulith-events-api")
    implementation("org.springframework.modulith:spring-modulith-starter-core")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.integration:spring-integration-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
}
