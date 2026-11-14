// HTTP edge shared by every API: RFC 7807 errors, correlation id, request log.
plugins {
  id("trellis.spring-module")
}

dependencies {
  api(project(":trellis-common"))
  api(libs.spring.boot.starter.webmvc)
  api(libs.spring.boot.starter.validation)
  // OptimisticLockingFailureException is mapped to 409 without pulling in JDBC.
  implementation("org.springframework:spring-tx")
}
