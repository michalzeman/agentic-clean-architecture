plugins {
    id("io.spring.dependency-management")
}

dependencies {
    // Spring Integration JDBC - provides JdbcChannelMessageStore and query providers
    api("org.springframework.integration:spring-integration-jdbc")

    // PostgreSQL driver
    api("org.postgresql:postgresql")

    // Spring JDBC support
    api("org.springframework:spring-jdbc")
}
