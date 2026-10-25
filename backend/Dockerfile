FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system app && useradd --system --gid app app \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl libssl3 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
RUN mkdir -p /app/var/media && chown app:app /app/var/media
ENV MEDIA_STORAGE_DIRECTORY=/app/var/media
COPY --from=build /app/target/lilimi-backend-0.0.1-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
