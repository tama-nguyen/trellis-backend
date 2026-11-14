// Aggregate tasks for the repo. Each one fans out to every Gradle module, so the command people type
// does not change when a module is added, renamed or moved (decision T21: backend-only repo).
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
      // One flat directory per module (decision T25): trellis-common/, trellis-platform-web/, trellis-notice/, trellis-app/, ...
      "*/build.gradle.kts",
    )
    ktlint()
  }
}

tasks.register("lintCheck") {
  group = "verification"
  description = "Runs every formatter/linter in check mode (Spotless)."
  dependsOn(tasks.named("spotlessCheck"))
  dependsOn(subprojects.map { "${it.path}:spotlessCheck" })
}

tasks.register("lintFix") {
  group = "formatting"
  description = "Applies every auto-fixable lint and formatting rule."
  dependsOn(tasks.named("spotlessApply"))
  dependsOn(subprojects.map { "${it.path}:spotlessApply" })
}

tasks.register("format") {
  group = "formatting"
  description = "Alias of lintFix, kept because people type it."
  dependsOn("lintFix")
}

tasks.named("check") {
  dependsOn(subprojects.map { "${it.path}:check" })
}
