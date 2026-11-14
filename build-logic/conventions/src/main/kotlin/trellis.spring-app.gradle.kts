// Convention for runnable Spring Boot applications (bootJar, bootRun, build info for /actuator/info).
plugins {
  id("trellis.spring-module")
  id("org.springframework.boot")
}

springBoot {
  buildInfo()
}

tasks.named<Jar>("jar") {
  // Only the boot jar is a deliverable for an application module.
  enabled = false
}
