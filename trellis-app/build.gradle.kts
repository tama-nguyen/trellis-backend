// The runnable backend: assembles every platform module. Business modules are added as dependencies here.
plugins {
  id("trellis.spring-app")
}

dependencies {
  implementation(project(":trellis-common"))
  implementation(project(":trellis-platform"))
  implementation(project(":trellis-platform-web"))
  implementation(project(":trellis-platform-events"))
  // Business modules
  implementation(project(":trellis-identity"))
  implementation(project(":trellis-learning-content"))
  implementation(project(":trellis-notice"))
  // JPA maps the business aggregates (ADR 0004); PersistenceConfiguration points the scan at the modules.
  implementation(libs.spring.boot.starter.data.jpa)
  // OpenAPI document at /v3/api-docs (exported to contracts/openapi/openapi.json).
  implementation(libs.springdoc.openapi.webmvc.api)
  implementation(libs.spring.boot.starter.actuator)

  testImplementation(libs.spring.boot.testcontainers)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(libs.testcontainers.junit.jupiter)
  testImplementation(libs.archunit.junit5)
}

// API contracts (decision T12): ApiContractsExport boots the app (test profile, Testcontainers Postgres) and
// writes or checks contracts/openapi/openapi.json and contracts/graphql/schema.graphqls. The published
// npm and PyPI contract packages are generated from those committed files (docs/architecture/contracts.md).
val apiContractsTag = "api-contracts"

tasks.named<Test>("test") {
  useJUnitPlatform { excludeTags(apiContractsTag) }
}

fun registerApiContractsTask(
  name: String,
  mode: String,
  text: String,
) = tasks.register<Test>(name) {
  group = "api contracts"
  description = text
  testClassesDirs = sourceSets["test"].output.classesDirs
  classpath = sourceSets["test"].runtimeClasspath
  useJUnitPlatform { includeTags(apiContractsTag) }
  systemProperty("trellis.api-contracts.mode", mode)
  systemProperty(
    "trellis.api-contracts.stale-dir",
    layout.buildDirectory
      .dir("api-contracts")
      .get()
      .asFile.absolutePath,
  )
  // Inputs are the whole application plus the committed files; always run rather than model them.
  outputs.upToDateWhen { false }
}

registerApiContractsTask(
  "exportApiContracts",
  "write",
  "Writes contracts/openapi/openapi.json and contracts/graphql/schema.graphqls from the running app.",
)
val checkApiContracts =
  registerApiContractsTask(
    "checkApiContracts",
    "check",
    "Fails when contracts/openapi/openapi.json or contracts/graphql/schema.graphqls is stale.",
  )

tasks.named("check") { dependsOn(checkApiContracts) }
