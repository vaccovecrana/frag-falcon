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
 * Best-effort: uses passwordless sudo when available, otherwise prints a hint.
 * Production runs the flat tarball as root and needs no caps.
 */
/**
 * Re-applies cap_net_admin to every launcher copy. Copying fg_vmm drops its
 * file capabilities, and every dev/test run needs them to attach a TAP.
 *
 * Uses $SUDOPW (a password piped to sudo -S) when present, so it can run
 * unattended from Gradle; otherwise it prints the manual command. Production
 * runs the flat tarball as root and needs no caps.
 */
val setupCaps = tasks.register<Exec>("setupCaps") {
  dependsOn(nativeBuild)
  workingDir = projectDir
  val pw = System.getenv("SUDOPW")
  commandLine(
    "bash", "-c",
    if (pw != null && pw.isNotEmpty()) {
      "printf '%s\\n' \"\$SUDOPW\" | sudo -S -p '' bash setup-caps.sh"
    } else {
      "sudo -n bash setup-caps.sh || echo 'run: sudo bash ff-jni/setup-caps.sh'"
    }
  )
  isIgnoreExitValue = true
}

tasks.processResources {
  dependsOn(nativeBuild)
  from("out") {
    include("fg_jni.so", "fg_vmm")
    into("io/vacco/ff")
  }
}

// Copying fg_vmm drops its file capabilities; re-apply them after every sync.
installNative.configure { finalizedBy(setupCaps) }
