FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

# Layered extraction (the jar is already built with layers.idx by default):
# dependencies and the Spring Boot loader itself change far less often than
# this project's own classes, so splitting them into separate COPY layers
# below means a rebuild after an ordinary code change only re-pushes the
# small "application" layer, not every dependency jar again.
FROM eclipse-temurin:25-jre AS layers
WORKDIR /workspace
COPY --from=build /workspace/target/outbreak-signal-service-*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN useradd --system --uid 10001 app
WORKDIR /app
# The "application" layer's jar is thin (this project's own classes only)
# and finds its dependencies via its own manifest Class-Path, each entry a
# path like "lib/spring-boot-4.1.1.jar" relative to wherever the jar itself
# sits — which is why "dependencies" has to land at ./lib, not ./, and the
# jar gets a fixed name so the ENTRYPOINT below never has to know the
# project's version number.
COPY --from=layers /workspace/extracted/dependencies/ ./
COPY --from=layers /workspace/extracted/snapshot-dependencies/ ./
COPY --from=layers /workspace/extracted/application/*.jar ./app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
