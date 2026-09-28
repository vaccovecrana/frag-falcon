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
