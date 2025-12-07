description = "Bank Account Domain Module"

dependencies {
    api(project(":shared-kernel:shared-domain"))

    // Jackson annotations for JSON serialization of sealed classes
    implementation("com.fasterxml.jackson.core:jackson-annotations")
}
