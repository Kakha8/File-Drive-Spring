# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .

COPY src ./src
# Resolve through the project build; go-offline separately traverses dependency
# ranges and can select unavailable snapshots. Cache downloads across builds.
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests package

# ---- Run stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
