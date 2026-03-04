# Stage 1 — build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN apk add --no-cache maven && \
    mvn -q clean package -DskipTests

# Stage 2 — run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S venue && adduser -S venue -G venue

COPY --from=builder /app/target/*.jar app.jar

RUN chown venue:venue app.jar
USER venue

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
