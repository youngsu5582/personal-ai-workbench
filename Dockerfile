# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x ./gradlew

COPY src ./src
RUN ./gradlew bootJar --no-daemon \
    && find build/libs -maxdepth 1 -type f -name '*.jar' -not -name '*-plain.jar' -exec cp {} /tmp/app.jar \;

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --create-home --uid 10001 workbench \
    && mkdir -p /app/var/assets \
    && chown -R workbench:workbench /app
COPY --from=build --chown=workbench:workbench /tmp/app.jar /app/app.jar

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
