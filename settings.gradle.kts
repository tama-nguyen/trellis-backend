// Root of the Trellis backend build (decision T21: the backend is its own repo; the web and Python
// repos consume the published contract packages instead of sharing this build).
pluginManagement {
  includeBuild("build-logic")
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}

plugins {
  id("com.diffplug.spotless") version "8.11.0" apply false
}

dependencyResolutionManagement {
  repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
  repositories {
    mavenCentral()
  }
}

rootProject.name = "trellis"

// One flat directory per module at the repo root, named after the Gradle project without the
// `trellis-` prefix (decision T25): :trellis-platform-web lives in platform-web/.
val modules =
  listOf(
    "trellis-common",
    "trellis-platform",
    "trellis-platform-events",
    "trellis-platform-web",
    "trellis-notice",
    "trellis-app",
  )

modules.forEach { name ->
  include(name)
  project(":$name").projectDir = file(name.removePrefix("trellis-"))
}
