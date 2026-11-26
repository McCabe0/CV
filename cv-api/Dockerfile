# Base images are pinned by digest, so they only change through dependabot. See decisions/cve.md.
FROM eclipse-temurin:25-jdk-noble@sha256:4ddc1a8bdaf283421df5487411aff10b797ccf1e06a339041005114c8f489b5c AS builder
WORKDIR /app

# The build files first, so a change to the code does not download the dependencies again.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
COPY domain/build.gradle.kts domain/
COPY application/build.gradle.kts application/
COPY adapters/build.gradle.kts adapters/
COPY app/build.gradle.kts app/
# Gradle's cache is kept between builds, so a download that failed part way is not started again.
RUN --mount=type=cache,target=/root/.gradle ./gradlew dependencies -Dorg.gradle.daemon=false > /dev/null

COPY . .
RUN --mount=type=cache,target=/root/.gradle ./gradlew :app:bootJar -Dorg.gradle.daemon=false

FROM eclipse-temurin:25-jre-alpine@sha256:69c2a26e9894016e274c880fa542956d005b3755f84db765145185e87405bd89

# Fixed in alpine, not yet in the base image: CVE-2026-4738 (zlib, critical), and CVE-2026-124317,
# -134703 and -128241 (libexpat, medium). Goes once dependabot moves the base image to one that has them.
RUN apk upgrade --no-cache zlib libexpat

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
