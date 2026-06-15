# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml ./pom.xml
COPY gateway/pom.xml ./gateway/pom.xml
COPY url-service/pom.xml ./url-service/pom.xml
COPY url-service/src ./url-service/src
RUN mvn clean package -DskipTests -pl url-service -am

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/url-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
