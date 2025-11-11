pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    plugins {
        id("com.google.protobuf") version providers.gradleProperty("googleProtobufPluginVersion").get()
        kotlin("jvm") version providers.gradleProperty("kotlinVersion").get()
        kotlin("plugin.spring") version providers.gradleProperty("kotlinVersion").get()
        id("io.spring.dependency-management") version providers.gradleProperty("springDependencyManagementPluginVersion").get()
        id("org.springframework.boot") version providers.gradleProperty("springBootPluginVersion").get()
        id("com.diffplug.spotless") version providers.gradleProperty("spotlessPluginVersion").get()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version providers.gradleProperty("foojayResolverPluginVersion").get()
}

rootProject.name = "agentic-clean-architecture"

file(rootDir)
    .walk()
    .maxDepth(10) // Adjust depth as needed
    .filter { it.isDirectory && File(it, "build.gradle.kts").exists() }
    .forEach { include(it.relativeTo(rootDir).path.replace(File.separator, ":")) }