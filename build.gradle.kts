plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("io.spring.dependency-management")
    id("com.diffplug.spotless")
}

allprojects {
    repositories {
        mavenCentral()
        maven { url = uri("https://plugins.gradle.org/m2/") }
    }
}

subprojects {

    this.subprojects.forEach { println("subproject: $it") }

    apply {
        plugin("java")
        plugin("kotlin")
        plugin("java-library")
        plugin("io.spring.dependency-management")
        plugin("org.jetbrains.kotlin.plugin.spring")
        plugin("com.diffplug.spotless")
    }

    group = "mz.data.platform"
    version = "0.0.1-SNAPSHOT"

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    spotless {
        kotlin {
            target("**/*.kt")
            ktlint()
            trimTrailingWhitespace()
            endWithNewline()
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }


    dependencyManagement {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:${property("springBootVersion")}")
            mavenBom("org.springframework.integration:spring-integration-bom:${property("springIntegrationVersion")}")
            mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
            mavenBom("org.jetbrains.kotlin:kotlin-bom:${property("kotlinVersion")}")
            mavenBom("org.jetbrains.kotlinx:kotlinx-coroutines-bom:${property("kotlinxCoroutinesBomVersion")}")
        }
    }

    dependencies {
        // GRPC support
        implementation("io.grpc:grpc-kotlin-stub:${property("protocGenGrpcKotlin")}")
        implementation("io.grpc:grpc-protobuf:${property("protocGenGrpcJavaVersion")}")
        implementation("io.grpc:grpc-stub:${property("protocGenGrpcJavaVersion")}")
        implementation("com.google.protobuf:protobuf-kotlin:${property("protobufProtocVersion")}")
        implementation("com.google.protobuf:protobuf-java-util:${property("protobufProtocVersion")}")
        implementation("com.hubspot.jackson:jackson-datatype-protobuf:${property("jacksonDatatypeProtobufVersion")}")

        // Kotlin and Reactor
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
        implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
        implementation("org.jetbrains.kotlin:kotlin-reflect")


        // Development and Test Dependencies
        testImplementation("io.projectreactor:reactor-test")
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")

        testImplementation("org.mockito:mockito-core:${property("mockitoCoreVersion")}")
        testImplementation("org.mockito.kotlin:mockito-kotlin:${property("mockitoKotlinVersion")}")

        testImplementation("org.junit.jupiter:junit-jupiter-params")

        // https://mvnrepository.com/artifact/org.assertj/assertj-core
        testImplementation("org.assertj:assertj-core:3.27.3")
    }

    kotlin {
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict")
        }
    }

    tasks.test {
        useJUnitPlatform {
            excludeTags("systemChecks")
        }
    }

    tasks.register<Test>("systemTest") {
        group = "verification"
        description = "Runs system integration tests against external services"
        useJUnitPlatform {
            includeTags("systemChecks")
        }
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
    }
}

// Root project system test task that orchestrates docker and bank-system-tests
tasks.register("systemTest") {
    group = "verification"
    description = "Runs system integration tests with docker-compose services"
    dependsOn(":bank-account:bank-account-boot-app:bootJar")
    dependsOn(":bank-transaction:bank-transaction-boot-app:bootJar")
    dependsOn(":bank-system-tests:systemTest")
}

tasks.register<Exec>("dockerComposeUp") {
    group = "Docker"
    description = "Starts docker-compose services with system-tests profile"
    dependsOn(":bank-account:bank-account-boot-app:bootJar")
    dependsOn(":bank-transaction:bank-transaction-boot-app:bootJar")

    commandLine("docker", "compose", "--profile", "system-tests", "up", "-d", "--wait")
}

tasks.register<Exec>("tearDownDockerCompose") {
    group = "docker"
    description = "Stops and removes docker-compose services"

    commandLine("docker", "compose", "--profile", "system-tests", "down", "-v")
}