import com.github.gradle.node.npm.task.NpmTask

plugins {
  id("io.vacco.oss.gitflow")
  id("com.github.node-gradle.node") version libs.versions.nodePl
}

configure<io.vacco.oss.gitflow.GsPluginProfileExtension> {
  sharedLibrary(true, false)
}

node {
  download.set(true)
  version.set("22.17.0")
}

val buildTaskUsingNpm = tasks.register<NpmTask>("buildNpm") {
  dependsOn(tasks.npmInstall)
  npmCommand.set(listOf("run", "build"))
  inputs.dir("./src")
  inputs.dir("./res")
  outputs.dir("./build/ui")
}

val copyBundle = tasks.register<Copy>("copyBundle") {
  dependsOn(buildTaskUsingNpm)
  from("./build/ui")
  from("./res/favicon.svg")
  from("./res/index.html")
  into("./build/resources/main/ui")
}

tasks.processResources {
  dependsOn(copyBundle)
  filesMatching("ui/version") {
    expand("projectVersion" to version)
  }
}

/**
 * Browser E2E suite against a running hypervisor. Not wired into `build`
 * (it requires a live UI); run it explicitly:
 *
 *   FF_UI_URL=http://127.0.0.1:7070 gradle :ff-ui:e2eTest
 */
tasks.register<NpmTask>("e2eTest") {
  description = "Runs the puppeteer/node:test browser suite against a running UI"
  group = "verification"
  dependsOn(tasks.npmInstall)
  npmCommand.set(listOf("run", "test:e2e"))
  environment.put("FF_UI_URL", System.getenv("FF_UI_URL") ?: "http://127.0.0.1:7070")
}

/**
 * Captures per-screen screenshots (desktop + mobile) for visual inspection.
 * Not part of `build`; requires a running hypervisor:
 *
 *   gradle :ff-ui:visual
 *
 * Artifacts land in ff-ui/build/test-artifacts/visual/.
 */
tasks.register<NpmTask>("visual") {
  description = "Captures UI screenshots for visual inspection against a running UI"
  group = "verification"
  dependsOn(tasks.npmInstall)
  npmCommand.set(listOf("run", "visual"))
  environment.put("FF_UI_URL", System.getenv("FF_UI_URL") ?: "http://127.0.0.1:7070")
}
