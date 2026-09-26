configure<io.vacco.oss.gitflow.GsPluginProfileExtension> {
  sharedLibrary(false, false)
  addJ8Spec()
}

tasks.withType<Test> {
  jvmArgs("--enable-native-access=ALL-UNNAMED")
}
