# Simple Dockerfile for bank-account-service
# Requires pre-built JAR: ./gradlew :bank-account:bank-account-boot-app:bootJar
FROM eclipse-temurin:21-jre-alpine

# Install curl for health checks
RUN apk add --no-cache curl

WORKDIR /app

# Copy the pre-built jar
COPY bank-account/bank-account-boot-app/build/libs/*.jar app.jar

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Expose the application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=5s --timeout=3s --retries=10 --start-period=30s \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
