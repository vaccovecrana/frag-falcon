configure<io.vacco.oss.gitflow.GsPluginProfileExtension> {
  sharedLibrary(false, false)
}

val libDir = "src/main/resources/io/vacco/ff"

val nativeBuild = tasks.register<Exec>("nativeBuild") {
  workingDir = projectDir
  commandLine("make")
  inputs.dir("src")
  outputs.dir("out")
}

/** Gathers every native artifact (launcher, JNI shim, libkrun libs) into one dir. */
val installNative = tasks.register<Sync>("installNative") {
  dependsOn(nativeBuild)
  into(layout.buildDirectory.dir("native"))
  from("out") { include("fg_jni.so", "fg_vmm") }
  from(libDir) { include("libkrun.so.2", "libkrun_init.so", "libkrunfw.so.5") }
  filePermissions { unix("0755") }
}

tasks.processResources {
  dependsOn(nativeBuild)
  from("out") {
    include("fg_jni.so", "fg_vmm")
    into("io/vacco/ff")
  }
}
