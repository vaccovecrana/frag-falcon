configure<io.vacco.oss.gitflow.GsPluginProfileExtension> {
  sharedLibrary(false, false)
}

val api by configurations

dependencies {
  api(project.ext.get("shax").toString())
  api("com.google.code.gson:gson:2.11.0")
  api("am.ik.yavi:yavi:0.14.1")
  api("io.vacco.ronove:rv-kit-murmux:1.2.6_2.2.5")
  api(project(":ff-native"))
  // api(project(":ff-ui"))
}
