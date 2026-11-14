// Transactional outbox, relay, transports (kafka | inmemory), consumer framework with inbox, retry and DLQ.
plugins {
  id("trellis.spring-module")
}

dependencies {
  api(project(":trellis-common"))
  api(project(":trellis-platform"))
  implementation("tools.jackson.core:jackson-databind")
  implementation("io.micrometer:micrometer-core")
  api(libs.spring.boot.starter.kafka)

  // Contract test: a serialised envelope must validate against contracts/events/envelope.schema.json.
  testImplementation(libs.json.schema.validator)
}
