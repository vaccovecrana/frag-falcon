import com.github.gradle.node.npm.task.NpmTask

plugins {
  id("com.github.node-gradle.node") version "7.0.1"
}

node {
  download.set(true)
  version.set("18.16.0")
}

val buildTaskUsingNpm = tasks.register<NpmTask>("buildNpm") {
  dependsOn(tasks.npmInstall)
  npmCommand.set(listOf("run", "build"))
  inputs.dir("./@ff")
  outputs.dir("./build/ui")
}

val copyJs = tasks.register<Copy>("copyJs") {
  dependsOn(buildTaskUsingNpm)
  from("./build/ui")
  from("./res/favicon.svg")
  into("./build/resources/main/ui")
}

val copyTs = tasks.register<Copy>("copyTs") {
  dependsOn(buildTaskUsingNpm)
  from("./@ff")
  into("./build/resources/main/ui/@ff")
}

val copyRes = tasks.register<Copy>("copyRes") {
  dependsOn(buildTaskUsingNpm)
  from("./node_modules/simple-line-icons/fonts")
  into("./build/resources/main/ui/fonts")
}

tasks.processResources {
  dependsOn(copyJs)
  dependsOn(copyTs)
  dependsOn(copyRes)
  filesMatching("ui/version") {
    expand("projectVersion" to version)
  }
}
