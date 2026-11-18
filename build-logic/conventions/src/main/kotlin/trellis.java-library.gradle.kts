// Base convention for every JVM module: Java 21 toolchain, strict javac, Spotless, JUnit 5.
// Lombok is available to every module (compile-time only: it is not on the runtime classpath).
// Use it for what records cannot express - @Slf4j, @RequiredArgsConstructor on Spring beans,
// @Builder on a multi-field value - and keep records for plain data. It needs JDK 21: it has broken
// on newer JDKs before (decisions.md T9). See AGENTS.md "Code standards (Java)".
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

// Test fixtures (a module opts in with the `java-test-fixtures` plugin) need the same BOM: their compile
// classpath inherits the module's own versionless dependencies.
pluginManager.withPlugin("java-test-fixtures") {
  listOf("testFixturesImplementation", "testFixturesCompileOnly", "testFixturesAnnotationProcessor")
    .forEach { configuration -> dependencies.add(configuration, dependencies.platform(springBootBom)) }
}

dependencies {
  val lombok = libs.findLibrary("lombok").orElseThrow()
  "compileOnly"(lombok)
  "annotationProcessor"(lombok)
  "testCompileOnly"(lombok)
  "testAnnotationProcessor"(lombok)

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
  // Contract tests read JSON Schemas straight from the repo's contracts/ directory.
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
