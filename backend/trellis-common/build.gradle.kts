// Pure Java: no Spring, no Jackson. Anything here must be usable from a plain main() or a test.
plugins {
  id("trellis.java-library")
}

dependencies {
  // The only library allowed here: @LogExecution declares its level as an slf4j Level, and slf4j-api is a
  // facade, not a framework (ArchUnit rule `commonHasNoFrameworkDependencies`).
  api("org.slf4j:slf4j-api")
}
