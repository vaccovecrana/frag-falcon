plugins {
  id("io.vacco.ronove") version libs.versions.ronove
}

configure<io.vacco.oss.gitflow.GsPluginProfileExtension> {
  sharedLibrary(false, false)
}

configure<io.vacco.ronove.plugin.RvPluginExtension> {
  optionalFields = true
  controllerClasses = arrayOf("io.vacco.ff.api.FgApiHdl")
  outFile.set(file("../ff-ui/src/rpc.ts"))
  reflectConfigFile.set(file("../ff-app/src/main/resources/reflect-config.json"))
  reachabilityMetadataFile.set(file("../ff-app/src/main/resources/reachability-metadata.json"))
}

val api by configurations

dependencies {
  api(project(":ff-jni"))
  api(project(":ff-ui"))
  api(libs.gson)
  api(libs.shax)
  api(libs.yavi)
  api(libs.ronovemx)
}