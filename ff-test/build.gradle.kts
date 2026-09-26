configure<io.vacco.oss.gitflow.GsPluginProfileExtension> {
  addJ8Spec()
}

dependencies {
  implementation(project(":ff-jni"))
  implementation(project(":ff-oci"))
}

tasks.withType<Test> {
  jvmArgs("--enable-native-access=ALL-UNNAMED")
  testLogging { showStandardStreams = true }
}
