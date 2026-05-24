FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/engine-0.0.1-SNAPSHOT.jar /app/engine.jar
ENTRYPOINT ["java", "-jar", "/app/engine.jar"]
