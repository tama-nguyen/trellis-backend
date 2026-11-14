// Convention for platform modules built on Spring: adds the configuration-properties processor (its
// metadata feeds the config-classification guardrail) and the Spring test starter.
plugins {
  id("trellis.java-library")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
  "annotationProcessor"(libs.findLibrary("spring-boot-configuration-processor").get())
  "testImplementation"(libs.findLibrary("spring-boot-starter-test").get())
}
