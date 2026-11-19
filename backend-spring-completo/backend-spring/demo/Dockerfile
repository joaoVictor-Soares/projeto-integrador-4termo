# ---------- build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# ---------- runtime ----------
FROM eclipse-temurin:21-jre
# ffmpeg: captura da webcam (v4l2); curl: healthcheck
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /storage/recusadas
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
