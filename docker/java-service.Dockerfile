# One Dockerfile for every Java service, selected with a build argument:
#   docker build --build-arg SERVICE=checkout-service -f docker/java-service.Dockerfile .

# --- Build stage: Maven builds only the requested module and the modules it depends on (-am).
FROM maven:3.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl "${SERVICE}" -am -DskipTests package

# --- Runtime stage: JRE only (no JDK, no Maven, no sources), non-root user.
FROM eclipse-temurin:21-jre-alpine
ARG SERVICE
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/${SERVICE}/target/${SERVICE}.jar app.jar
USER app
ENV SERVER_PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
