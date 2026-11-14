// Pure Java: no Spring, no Jackson. Anything here must be usable from a plain main() or a test.
plugins {
  id("trellis.java-library")
  // MutableClock is a test utility, not runtime code (audit C13): it ships in the test-fixtures jar, which
  // consumers pull with testImplementation(testFixtures(project(":trellis-common"))).
  `java-test-fixtures`
}

dependencies {
  // The only library allowed here: @LogExecution declares its level as an slf4j Level, and slf4j-api is a
  // facade, not a framework (ArchUnit rule `commonHasNoFrameworkDependencies`).
  api("org.slf4j:slf4j-api")
}
