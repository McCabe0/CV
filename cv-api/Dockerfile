FROM eclipse-temurin:25-jdk-noble AS builder
WORKDIR /app

# The build files first, so a change to the code does not download the dependencies again.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
COPY domain/build.gradle.kts domain/
COPY application/build.gradle.kts application/
COPY adapters/build.gradle.kts adapters/
COPY app/build.gradle.kts app/
RUN ./gradlew dependencies -Dorg.gradle.daemon=false > /dev/null

COPY . .
RUN ./gradlew :app:bootJar -Dorg.gradle.daemon=false

FROM eclipse-temurin:25-jre-alpine

RUN addgroup --gid 2000 --system appgroup && \
    adduser --uid 2000 --system appuser -G appgroup

WORKDIR /app
COPY --from=builder /app/app/build/libs/cv-api.jar /app/app.jar
RUN chown -R appuser:appgroup .
USER appuser

# The commit, on all its telemetry and on the image. Last, so a new commit rebuilds nothing above.
ARG GIT_SHA=unknown
LABEL org.opencontainers.image.revision=$GIT_SHA
ENV SERVICE_VERSION=$GIT_SHA

ENTRYPOINT ["java", "-XX:+AlwaysActAsServerClassMachine", "-jar", "/app/app.jar"]
