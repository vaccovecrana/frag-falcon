plugins {
  application
  id("org.graalvm.buildtools.native") version "1.1.13"
}

dependencies {
  implementation(project(":ff-api"))
}

application {
  mainClass.set("io.vacco.ff.FgMain")
}

graalvmNative {
  binaries {
    named("main") {
      configurationFileDirectories.from(file("src/main/graal"))
      buildArgs.add("--enable-url-protocols=http,https")
      buildArgs.add("-march=compatibility")
    }
  }
}

/**
 * Flat, self-contained release distribution: the hypervisor executable plus the
 * native launcher and libkrun shared objects, all in one directory.
 */
val distNativeTar = tasks.register<Tar>("distNativeTar") {
  group = "distribution"
  description = "Bundles the native executable and its native runtime into a tar.gz"
  dependsOn(tasks.named("nativeCompile"), ":ff-jni:installNative")
  compression = Compression.GZIP
  archiveFileName.set("frag-falcon-${project.version}.tar.gz")
  destinationDirectory.set(layout.buildDirectory.dir("distributions"))
  into("frag-falcon-${project.version}") {
    from(layout.buildDirectory.dir("native/nativeCompile")) { include("flc") }
    from(project(":ff-jni").layout.buildDirectory.dir("native")) {
      include("fg_vmm", "fg_jni.so", "libkrun.so.2", "libkrun_init.so", "libkrunfw.so.5")
    }
  }
  filePermissions { unix("0755") }
}
/*
tasks.named("assemble") {
  dependsOn(distNativeTar)
}
*/