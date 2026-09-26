configure<io.vacco.oss.gitflow.GsPluginProfileExtension> {
  sharedLibrary(false, false)
}

val api by configurations

dependencies {
  api("com.google.code.gson:gson:2.11.0")
  api("org.slf4j:slf4j-api:2.0.18")
}
