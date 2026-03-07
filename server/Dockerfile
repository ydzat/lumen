# --- Build stage ---
FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY . .
RUN bash scripts/download-model.sh
RUN ./gradlew :server:buildFatJar --no-daemon

# --- Run stage ---
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache libc6-compat
WORKDIR /app
COPY --from=build /app/server/build/libs/lumen-server.jar /app/lumen-server.jar
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "/app/lumen-server.jar"]
