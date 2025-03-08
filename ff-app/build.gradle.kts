plugins {
  application
  id("org.graalvm.buildtools.native") version "0.10.2"
}

dependencies {
  implementation(project(":ff-api"))
  implementation("org.graalvm.nativeimage:svm:23.1.6")
}

application { mainClass.set("io.vacco.ff.FgMain") }

graalvmNative {
  binaries {
    named("main") {
      configurationFileDirectories.from(file("src/main/resources"))
      buildArgs.add("--enable-url-protocols=http,https")
      buildArgs.add("--add-exports org.graalvm.nativeimage.builder/com.oracle.svm.core.jdk=ALL-UNNAMED")
      buildArgs.add("--add-exports org.graalvm.nativeimage.builder/com.oracle.svm.hosted=ALL-UNNAMED")
      buildArgs.add("-H:+UnlockExperimentalVMOptions")
      buildArgs.add("--features=io.vacco.ff.graal.FgFeature")
      buildArgs.add("-H:NativeLinkerOption=./out/fg_jni.o")
      buildArgs.add("-H:NativeLinkerOption=./out/fg_proc.o")
      buildArgs.add("-H:NativeLinkerOption=./out/fg_raw.o")
      buildArgs.add("-H:NativeLinkerOption=./out/fg_tap.o")
      buildArgs.add("-H:NativeLinkerOption=./out/fg_unix.o")
      buildArgs.add("-H:NativeLinkerOption=./out/fg_vsock.o")
      buildArgs.add("-march=compatibility")
    }
  }
}
