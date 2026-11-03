#FROM ubuntu:latest
#LABEL authors="allan"

#ENTRYPOINT ["top", "-b"]
#Application Building
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies opportunistically; do not fail the image build if Maven Central is unreachable here.
RUN mvn dependency:go-offline -B || true
COPY src ./src
RUN mvn clean package -DskipTests

#runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar" , "app.jar"]
