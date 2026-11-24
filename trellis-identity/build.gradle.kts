// Native account lifecycle and external identity integration. Passwords and raw tokens never leave this module.
plugins {
  id("trellis.spring-module")
}

dependencies {
  implementation(project(":trellis-common"))
  implementation(project(":trellis-platform"))
  implementation(project(":trellis-platform-web"))
  implementation(libs.spring.boot.starter.data.jpa)
  implementation("org.springframework.boot:spring-boot-starter-mail")
  implementation("io.micrometer:micrometer-observation")
  implementation("org.springframework:spring-tx")
}
