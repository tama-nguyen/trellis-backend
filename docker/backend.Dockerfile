# syntax=docker/dockerfile:1.28.0
#
# Backend image. Packages the boot jar that Gradle already built; it never compiles, so the Gradle
# build cache is shared between laptops and CI (plan 10 A2.2, DataHub docker/datahub-gms/Dockerfile).
#
#   ./gradlew :trellis-app:bootJar
#   docker buildx build -f docker/backend.Dockerfile -t trellis/backend:local .
#
# Build context = repo root, filtered by docker/backend.Dockerfile.dockerignore (only the jar goes in).
#
# Targets:
#   prod (default, last stage)  layered app, JarLauncher
#   dev                         JRE + user only; compose profile `debug` bind-mounts the jar and adds JDWP
#
# Build args:
#   JRE_IMAGE            pinned by tag + digest; bump both together (Renovate later)
#   OTEL_AGENT_VERSION   empty = no agent in the image. Set it (and OTEL_AGENT_SHA256) to bake the
#                        OpenTelemetry Java agent into /app/otel/; enable it at runtime by adding
#                        -javaagent:/app/otel/opentelemetry-javaagent.jar to JAVA_TOOL_OPTIONS.
#                        Spring Boot's own OTel starter is the default, so the agent is opt-in.

ARG JRE_IMAGE=eclipse-temurin:21.0.13_9-jre-noble@sha256:0d57a8e90c2bb844c5a8e1b2b71e11eff06dad7edcfd803cd21ed4ae203b2515

FROM ${JRE_IMAGE} AS base
# The image ships curl (healthcheck) already. A fixed high UID keeps file ownership predictable on bind
# mounts and avoids clashing with the image's own `ubuntu` user (UID 1000).
# Patch OS packages fixed after the pinned base digest was published (DataHub does `apk upgrade`).
# Bump OS_PKG_REFRESH to force this layer to rebuild when a CVE fix lands; Trivy (ci/security.yml)
# tells you when.
ARG OS_PKG_REFRESH=2026-11-14
RUN apt-get update \
 && apt-get upgrade -y --no-install-recommends \
 && rm -rf /var/lib/apt/lists/*
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app \
 && mkdir -p /app/otel /app/tmp \
 && chown -R app:app /app
WORKDIR /app
ENV SERVER_PORT=8080 \
    MANAGEMENT_SERVER_PORT=8081 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -XX:+UseG1GC -Djava.io.tmpdir=/app/tmp -Dfile.encoding=UTF-8"
EXPOSE 8080 8081
# Readiness lives on the separate management port (decision T13), so a slow DB keeps the container
# "starting" instead of "healthy" and `compose up --wait` blocks on it.
HEALTHCHECK --start-period=90s --interval=10s --timeout=3s --retries=6 \
  CMD curl -fsS "http://127.0.0.1:${MANAGEMENT_SERVER_PORT}/actuator/health/readiness" >/dev/null || exit 1

# ---------------------------------------------------------------------------------------------------
FROM base AS otel-agent
ARG OTEL_AGENT_VERSION=""
ARG OTEL_AGENT_SHA256=""
RUN set -eu; \
    if [ -n "${OTEL_AGENT_VERSION}" ]; then \
      url="https://repo1.maven.org/maven2/io/opentelemetry/javaagent/opentelemetry-javaagent/${OTEL_AGENT_VERSION}/opentelemetry-javaagent-${OTEL_AGENT_VERSION}.jar"; \
      curl -fsSL -o /app/otel/opentelemetry-javaagent.jar "$url"; \
      if [ -z "${OTEL_AGENT_SHA256}" ]; then echo "OTEL_AGENT_SHA256 is required with OTEL_AGENT_VERSION" >&2; exit 1; fi; \
      echo "${OTEL_AGENT_SHA256}  /app/otel/opentelemetry-javaagent.jar" | sha256sum -c -; \
    fi

# ---------------------------------------------------------------------------------------------------
FROM base AS extract
# The plain jar is disabled in trellis.spring-app, so libs/ holds exactly one boot jar whatever its
# version suffix is. Fail loudly if that ever stops being true.
COPY trellis-app/build/libs/ /tmp/libs/
RUN set -eu; \
    set -- /tmp/libs/*.jar; \
    if [ "$#" -ne 1 ]; then echo "expected exactly one jar in build/libs, found: $*" >&2; exit 1; fi; \
    java -Djarmode=tools -jar "$1" extract --layers --launcher --destination /extracted

# ---------------------------------------------------------------------------------------------------
FROM base AS dev
USER app

# ---------------------------------------------------------------------------------------------------
FROM base AS prod
ARG VERSION=local
ARG REVISION=unknown
LABEL org.opencontainers.image.title="trellis-backend" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.licenses="Apache-2.0"
COPY --from=otel-agent --chown=app:app /app/otel/ /app/otel/
# Ordered by how often they change: a normal push only uploads the `application` layer.
COPY --from=extract --chown=app:app /extracted/dependencies/ ./
COPY --from=extract --chown=app:app /extracted/spring-boot-loader/ ./
COPY --from=extract --chown=app:app /extracted/snapshot-dependencies/ ./
COPY --from=extract --chown=app:app /extracted/application/ ./
USER app
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
