// Base Spring infrastructure shared by every deployable: persistence (native SQL over JdbcClient),
// observability, authorization/actor resolution and the AOP aspects (decisions T18, T19). Deliberately free of
// servlet, GraphQL and Kafka so a consumer or upgrade job can run without the HTTP edge.
plugins {
  id("trellis.spring-module")
}

dependencies {
  api(project(":trellis-common"))
  api(libs.spring.boot.starter.jdbc)
  api(libs.spring.boot.starter.liquibase)
  api(libs.spring.boot.starter.actuator)
  api(libs.spring.boot.starter.opentelemetry)
  api(libs.spring.boot.starter.aspectj)
  // Authorization and actor resolution: the JWT decoding bits, without spring-security-web (no servlet here).
  api("org.springframework.security:spring-security-core")
  api("org.springframework.security:spring-security-oauth2-jose")
  api("org.springframework.security:spring-security-oauth2-resource-server")
  implementation("io.micrometer:micrometer-core")
  implementation(libs.micrometer.registry.prometheus)
  implementation("io.micrometer:context-propagation")
  implementation("tools.jackson.core:jackson-databind")
  implementation(libs.caffeine)
  implementation(libs.bucket4j.core)
  // Redis-backed buckets are optional: only active when trellis.rate-limit.store=redis.
  implementation(libs.bucket4j.lettuce)
  implementation(libs.lettuce.core)
  runtimeOnly(libs.postgresql)
}
