# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy all POMs first (for dependency caching)
COPY pom.xml ./pom.xml
COPY gateway/pom.xml ./gateway/pom.xml
COPY url-service/pom.xml ./url-service/pom.xml

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl gateway -am -q || true

# Copy source code
COPY gateway/src ./gateway/src
COPY url-service/src ./url-service/src

# Build only gateway module
RUN mvn package -DskipTests -pl gateway -am -q

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
