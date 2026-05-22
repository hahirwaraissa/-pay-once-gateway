# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
# Pre-fetch dependencies to speed up subsequent builds
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests

# Stage 2: Create runtime container
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Add a non-root user for running the application securely
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy packaged JAR from the builder stage
COPY --from=builder /app/target/pay-once-gateway-0.0.1-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8080

# Run the jar file
ENTRYPOINT ["java", "-jar", "app.jar"]
