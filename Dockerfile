# ---- Build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Descarga dependencias en una capa aparte para aprovechar la caché
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# ---- Runtime ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Usuario sin privilegios (hardening)
RUN addgroup -S app && adduser -S app -G app
COPY --from=build --chown=app:app /app/target/*.jar app.jar
USER app

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
