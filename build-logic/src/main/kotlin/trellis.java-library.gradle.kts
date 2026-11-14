// Base convention for every JVM module: Java 21 toolchain, strict javac, Spotless, JUnit 5.
// Lombok is deliberately not applied: records cover value types, and Lombok broke on newer JDKs before
// (decisions.md T9). See backend/AGENTS.md "Code standards".
plugins {
  `java-library`
  jacoco
  id("com.diffplug.spotless")
}

val libs = the<VersionCatalogsExtension>().named("libs")

group = "io.trellis"
version = "0.1.0-SNAPSHOT"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
}

// The Spring Boot BOM pins JUnit, Jackson, Micrometer, etc. for every module, Spring or not.
val springBootBom = libs.findLibrary("spring-boot-bom").get()
listOf("implementation", "annotationProcessor", "testImplementation", "testAnnotationProcessor", "compileOnly")
  .forEach { configuration -> dependencies.add(configuration, dependencies.platform(springBootBom)) }

dependencies {
  "testImplementation"("org.junit.jupiter:junit-jupiter")
  "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
  options.encoding = "UTF-8"
  // -parameters: SpEL keys in AOP annotations and Spring binding read real parameter names.
  // -processing: annotation processors warn about unclaimed annotations, which is noise.
  // -serial: exceptions are never Java-serialised across a trust boundary here.
  // -try: try-with-resources scopes (MDC, locks) intentionally leave the resource unused.
  options.compilerArgs.addAll(
    listOf("-parameters", "-Xlint:all", "-Xlint:-processing", "-Xlint:-serial", "-Xlint:-try", "-Werror"),
  )
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
  // Contract tests read JSON Schemas straight from the monorepo's contracts/ directory.
  systemProperty(
    "trellis.contracts.dir",
    rootProject.layout.projectDirectory
      .dir("contracts")
      .asFile.absolutePath,
  )
  testLogging {
    events("failed")
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
}

spotless {
  java {
    target("src/**/*.java")
    palantirJavaFormat(libs.findVersion("palantir-java-format").get().requiredVersion)
    removeUnusedImports()
    trimTrailingWhitespace()
    endWithNewline()
  }
}

// CI reads the XML coverage report; HTML is for people.
tasks.withType<JacocoReport>().configureEach {
  reports {
    xml.required = true
    html.required = true
  }
}
