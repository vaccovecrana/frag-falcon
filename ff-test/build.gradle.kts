configure<io.vacco.oss.gitflow.GsPluginProfileExtension> {
  addJ8Spec()
}

dependencies {
  implementation(project(":ff-krun"))
  implementation(project(":ff-oci"))
  implementation(project(":ff-vmm"))
}

tasks.withType<Test> {
  jvmArgs("--enable-native-access=ALL-UNNAMED")
  testLogging { showStandardStreams = true }
}
