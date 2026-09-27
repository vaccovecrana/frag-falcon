configure<io.vacco.oss.gitflow.GsPluginProfileExtension> {
  sharedLibrary(false, false)
}

val api by configurations

dependencies {
  api(project(":ff-jni"))
  api(project(":ff-oci"))
  api("com.google.code.gson:gson:2.11.0")
  api("io.vacco.shax:shax:2.0.18.1")
  api("am.ik.yavi:yavi:0.14.1")
}
