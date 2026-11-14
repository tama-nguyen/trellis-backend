// Included build that holds the convention plugins (trellis.java-library, trellis.spring-module,
// trellis.spring-app). It reads the same version catalog as the main build.
dependencyResolutionManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
  versionCatalogs {
    create("libs") { from(files("../../gradle/libs.versions.toml")) }
  }
}

rootProject.name = "conventions"
