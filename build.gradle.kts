// Monorepo aggregate tasks. Each one fans out to every component that participates; today that is the
// backend. web/ and workers/ hook in here later (decision T7) without changing the command people run.
plugins {
  base
  id("com.diffplug.spotless")
}

spotless {
  kotlinGradle {
    target(
      "*.gradle.kts",
      "build-logic/*.gradle.kts",
      "build-logic/src/main/kotlin/*.gradle.kts",
      "backend/*/build.gradle.kts",
      "backend/*/*/build.gradle.kts",
    )
    ktlint()
  }
}

val backendProjects = subprojects.filter { it.projectDir.startsWith(rootDir.resolve("backend")) }

tasks.register("lintCheck") {
  group = "verification"
  description = "Runs every formatter/linter in check mode (backend: Spotless)."
  dependsOn(tasks.named("spotlessCheck"))
  dependsOn(backendProjects.map { "${it.path}:spotlessCheck" })
}

tasks.register("lintFix") {
  group = "formatting"
  description = "Applies every auto-fixable lint and formatting rule (backend: Spotless)."
  dependsOn(tasks.named("spotlessApply"))
  dependsOn(backendProjects.map { "${it.path}:spotlessApply" })
}

tasks.register("format") {
  group = "formatting"
  description = "Alias of lintFix, kept because people type it."
  dependsOn("lintFix")
}

tasks.named("check") {
  dependsOn(backendProjects.map { "${it.path}:check" })
}
