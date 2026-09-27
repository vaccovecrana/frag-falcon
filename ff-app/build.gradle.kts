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
      configurationFileDirectories.from(file("src/main/resources"))
      buildArgs.add("--enable-url-protocols=http,https")
      buildArgs.add("-march=compatibility")
    }
  }
}