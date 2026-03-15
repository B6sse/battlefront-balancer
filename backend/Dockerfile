# build from repository root: docker build -f backend/Dockerfile
FROM eclipse-temurin:25-jdk-jammy AS build
WORKDIR /app

COPY gradle gradle
COPY gradlew gradlew
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY backend backend

RUN ./gradlew :backend:bootJar --no-daemon

FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

RUN useradd -m app
COPY --from=build /app/backend/build/libs/*.jar app.jar
RUN chown -R app:app /app

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
