// The HTTP edge shared by every API (decision T18): RFC 7807 errors, correlation id, request log, the security
// filter chain and Spring for GraphQL (decision T12). Only trellis-app depends on it; jobs ship without it.
plugins {
  id("trellis.spring-module")
}

dependencies {
  api(project(":trellis-common"))
  api(project(":trellis-platform"))
  api(libs.spring.boot.starter.webmvc)
  api(libs.spring.boot.starter.validation)
  api(libs.spring.boot.starter.graphql)
  api(libs.spring.boot.starter.security.oauth2.resource.server)
  implementation(libs.spring.boot.starter.actuator)
  implementation("io.micrometer:micrometer-core")
  // OptimisticLockingFailureException is mapped to 409 without pulling in JDBC.
  implementation("org.springframework:spring-tx")
}
