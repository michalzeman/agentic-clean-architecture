description = "Bank Account Contract Proto Module"

plugins {
    id("com.google.protobuf")
}

dependencies {
    api("com.google.protobuf:protobuf-kotlin:${property("protobufProtocVersion")}")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${property("protobufProtocVersion")}"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                named("java")
                create("kotlin")
            }
        }
    }
}

sourceSets {
    main {
        proto {
            srcDir("src/main/proto")
        }
    }
}

tasks.named<ProcessResources>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
