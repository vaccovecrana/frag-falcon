plugins {
  id("io.vacco.ronove") version "3.0.0"
}

configure<io.vacco.oss.gitflow.GsPluginProfileExtension> {
  sharedLibrary(false, false)
}

configure<io.vacco.ronove.plugin.RvPluginExtension> {
  optionalFields = true
  controllerClasses = arrayOf("io.vacco.ff.api.FgApiHdl")
  outFile.set(layout.buildDirectory.file("rpc.ts"))
}

val api by configurations

dependencies {
  api(project(":ff-jni"))
  api("com.google.code.gson:gson:2.11.0")
  api("io.vacco.shax:shax:2.0.18.1")
  api("am.ik.yavi:yavi:0.14.1")
  api("io.vacco.ronove:rv-kit-murmux:3.0.0")
}

tasks.named("classes") {
  finalizedBy("ronoveTypescriptRpc")
}
