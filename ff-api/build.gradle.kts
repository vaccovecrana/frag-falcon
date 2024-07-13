plugins {
  id("io.vacco.ronove") version "1.2.6"
  application
}

/*
configure<io.vacco.ronove.plugin.RvPluginExtension> {
  controllerClasses = arrayOf("io.grhk.web.GkApiHdl")
  outFile.set(file("../gk-ui/@gk/rpc.ts"))
}
*/
val api by configurations

dependencies {
  api(project(":ff-schema"))
  api("io.vacco.shax:shax:2.0.6.0.1.0")
  api("com.google.code.gson:gson:2.11.0")
  api("io.vacco.ronove:rv-kit-murmux:1.2.6_2.2.5")
  // runtimeOnly(project(":gk-ui"))
}

application {
  mainClass = "io.grhk.GkMain"
}
