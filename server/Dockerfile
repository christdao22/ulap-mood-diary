# The server image (README §7.1). Build the JAR first, then the image from the repo root:
#   ./gradlew :server:buildFatJar
#   docker build -f server/Dockerfile -t ulap-server:local .
# The same image runs the API and the one-off `migrate` step (infra/compose.yaml).
FROM eclipse-temurin:17.0.21_9-jre-alpine-3.24

WORKDIR /app
COPY server/build/libs/server-all.jar /app/app.jar

# Size the heap from the container's memory limit (ulap-reference.md §8.1).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080

# Never run as root.
USER 1000:1000
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
