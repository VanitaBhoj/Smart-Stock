FROM maven:3.9.9-eclipse-temurin-23 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:23-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/target/smartstock-0.0.1-SNAPSHOT.jar /app/smartstock.jar
EXPOSE 8080
USER 10001:10001
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=5 \
    CMD curl --fail --silent --user "$SMARTSTOCK_ADMIN_USERNAME:$SMARTSTOCK_ADMIN_PASSWORD" http://localhost:8080/api/products >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/smartstock.jar"]
