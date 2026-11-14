// Sample business module: the living example of every platform seam. Copy it when starting a real module
// (see README.md). Packages: api -> domain -> application -> infrastructure / web.
plugins {
  id("trellis.spring-module")
}

dependencies {
  implementation(project(":trellis-common"))
  implementation(project(":trellis-platform"))
  implementation(project(":trellis-platform-events"))
  implementation(project(":trellis-platform-web"))
  implementation("io.micrometer:micrometer-observation")
  implementation("org.springframework:spring-tx")
}
