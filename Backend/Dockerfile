FROM maven:3.9-eclipse-temurin-21@sha256:2b4496088e7b80ae10a8c9f74e574ea21380325a006ec684532ad6bad5bc7273 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c
WORKDIR /app
COPY --from=build /app/target/biopet-backend-0.1.0.jar app.jar
# Script de arranque exclusivo para Render (render.yaml -> dockerCommand:
# /app/render-entrypoint.sh), que construye DB_URL en runtime a partir de
# DB_HOST/DB_PORT/DB_NAME. Docker Compose local NO lo usa: sigue arrancando
# por el ENTRYPOINT normal de abajo, con DB_URL ya definido en .env.
COPY render-entrypoint.sh /app/render-entrypoint.sh
RUN chmod +x /app/render-entrypoint.sh
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
