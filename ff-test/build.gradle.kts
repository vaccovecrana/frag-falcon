configure<io.vacco.oss.gitflow.GsPluginProfileExtension> {
  addJ8Spec()
}

dependencies {
  implementation(project(":ff-api"))
}

tasks.withType<Test> {
  jvmArgs("--enable-native-access=ALL-UNNAMED")
  testLogging { showStandardStreams = true }
  dependsOn(":ff-jni:installNative", ":ff-jni:setupCaps")
  environment("FF_NATIVE_DIR", project(":ff-jni").layout.buildDirectory.dir("native").get().asFile.absolutePath)
}
