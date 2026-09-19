plugins {
    id("io.spring.dependency-management")
    id("java-test-fixtures")
}

dependencies {
    api(project(":shared-kernel:shared-domain"))
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("org.springframework.boot:spring-boot-starter-actuator")
    api("io.micrometer:micrometer-registry-prometheus")
    // Boot 4 tracing auto-config modules (versions from spring-boot-dependencies 4.0.0 BOM)
    api("org.springframework.boot:spring-boot-micrometer-observation")
    api("org.springframework.boot:spring-boot-micrometer-tracing")
    api("org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry")
    // OpenTelemetry SDK auto-config: provides the Resource + OpenTelemetrySdk beans
    // that spring-boot-micrometer-tracing-opentelemetry's SdkTracerProvider requires
    api("org.springframework.boot:spring-boot-opentelemetry")
    // Bridge + OTLP exporter (versions managed by micrometer-tracing-bom / opentelemetry-bom via the Boot BOM)
    api("io.micrometer:micrometer-tracing-bridge-otel")
    api("io.opentelemetry:opentelemetry-exporter-otlp")
    api("io.micrometer:context-propagation")
    api("org.springframework.integration:spring-integration-core")
    // Reactor classes for the context-propagation hook; version from the Boot BOM
    implementation("io.projectreactor:reactor-core")
    // JSON log encoder; NOT managed by the Boot 4.0.0 BOM (only logback.version is)
    implementation("net.logstash.logback:logstash-logback-encoder:${property("logstashLogbackEncoderVersion")}")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // SimpleTracer/SimpleCurrentTraceContext test kit; version managed by micrometer-tracing-bom via the Boot BOM
    testImplementation("io.micrometer:micrometer-tracing-test")
    // Shared SimpleW3CPropagator fixture consumed by connector-redis / connector-nats-jetstream tests
    testFixturesImplementation("io.micrometer:micrometer-tracing-test")
}
