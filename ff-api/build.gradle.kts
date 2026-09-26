plugins {
  id("io.vacco.ronove") version "1.2.6"
}

configure<io.vacco.oss.gitflow.GsPluginProfileExtension> {
  sharedLibrary(false, false)
}

configure<io.vacco.ronove.plugin.RvPluginExtension> {
  controllerClasses = arrayOf("io.vacco.ff.api.FgApiHdl")
  outFile.set(file("../ff-ui/@ff/rpc.ts"))
}

val api by configurations

dependencies {
  api("io.vacco.shax:shax:2.0.6.0.1.0")
  api("com.google.code.gson:gson:2.11.0")
  api("am.ik.yavi:yavi:0.14.1")
  api("io.vacco.ronove:rv-kit-murmux:1.2.6_2.2.5")
  api(project(":ff-jni"))
  api(project(":ff-ui"))
}

val copyFfRt = tasks.register<Copy>("copyFfRt") {
  from("./src/main/go/ffrt")
  from("./src/main/c/out/fgnet.so")
  into("./build/resources/main/io/vacco/ff")
}

tasks.processResources {
  dependsOn(copyFfRt)
}
