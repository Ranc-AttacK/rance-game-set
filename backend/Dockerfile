# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21 AS build
ARG MAVEN_SETTINGS=docker/maven-settings.xml
WORKDIR /build
COPY ${MAVEN_SETTINGS} /build/maven-settings.xml
COPY pom.xml .
COPY src ./src
RUN mvn -B -q -s /build/maven-settings.xml \
    -Dmaven.wagon.http.retryHandler.count=3 \
    package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app app
WORKDIR /app
COPY --from=build /build/target/gal-community-*.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
