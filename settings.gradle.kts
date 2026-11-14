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

// Backend modules live under backend/{common,platform/*,domains/*,apps/*} (plan 12 1.1) but keep short,
// flat Gradle project paths (:trellis-app:bootRun), so the directory layout can change without touching
// every build file, CI job or command in the docs.
val backendModules =
  mapOf(
    "trellis-common" to "common",
    "trellis-platform" to "platform/base",
    "trellis-platform-events" to "platform/events",
    "trellis-platform-web" to "platform/web",
    "trellis-notice" to "domains/notice",
    "trellis-app" to "apps/app",
  )

backendModules.forEach { (name, path) ->
  include(name)
  project(":$name").projectDir = file("backend/$path")
}
