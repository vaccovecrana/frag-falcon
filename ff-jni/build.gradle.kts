configure<io.vacco.oss.gitflow.GsPluginProfileExtension> {
  sharedLibrary(false, false)
}

val nativeBuild = tasks.register<Exec>("nativeBuild") {
  workingDir = projectDir
  commandLine("make")
  inputs.dir("src")
  outputs.dir("out")
}

tasks.processResources {
  dependsOn(nativeBuild)
  from("out") {
    include("fg_jni.so", "fg_vmm")
    into("io/vacco/ff")
  }
}
