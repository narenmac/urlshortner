# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy all POMs first (for dependency caching)
COPY pom.xml ./pom.xml
COPY gateway/pom.xml ./gateway/pom.xml
COPY url-service/pom.xml ./url-service/pom.xml

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl url-service -am -q || true

# Copy source code
COPY gateway/src ./gateway/src
COPY url-service/src ./url-service/src

# Build only url-service module
RUN mvn package -DskipTests -pl url-service -am -q

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/url-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
