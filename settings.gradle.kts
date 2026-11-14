// Root of the Trellis monorepo build (decisions T2, T7). Today Gradle drives backend/ only; web/ (pnpm)
// and workers/ (uv) are wired into the aggregate tasks in build.gradle.kts when they get Gradle shims.
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

// Backend modules live under backend/ but keep short project paths (:trellis-app:bootRun).
val backendModules =
  listOf(
    "trellis-common",
    "trellis-aop",
    "trellis-platform-web",
    "trellis-platform-security",
    "trellis-platform-observability",
    "trellis-platform-persistence",
    "trellis-platform-events",
    "trellis-platform-graphql",
    "trellis-notice",
    "trellis-app",
  )

backendModules.forEach { name ->
  include(name)
  project(":$name").projectDir = file("backend/$name")
}
