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

/**
 * Re-applies cap_net_admin to every launcher copy after a build. Sync/copy tasks
 * overwrite the binary and silently drop its file capabilities, which makes
 * unprivileged dev runs fail with "Operation not permitted" on tap creation.
 *
 * Run it with the privileges setcap needs, e.g.:
 *   sudo bash ff-jni/setup-caps.sh
 * or, if the invoking shell already has passwordless sudo/root:
 *   gradle :ff-jni:setupCaps
 *
 * A no-op in production, where the flat tarball is run as root and needs no caps.
 */
val setupCaps = tasks.register<Exec>("setupCaps") {
  dependsOn(nativeBuild, installNative, tasks.processResources)
  workingDir = projectDir
  commandLine("bash", "setup-caps.sh")
  isIgnoreExitValue = true
}

tasks.processResources {
  dependsOn(nativeBuild)
  from("out") {
    include("fg_jni.so", "fg_vmm")
    into("io/vacco/ff")
  }
}
