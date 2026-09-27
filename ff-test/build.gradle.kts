configure<io.vacco.oss.gitflow.GsPluginProfileExtension> {
  addJ8Spec()
}

dependencies {
  implementation(project(":ff-api"))
}

tasks.withType<Test> {
  jvmArgs("--enable-native-access=ALL-UNNAMED")
  testLogging { showStandardStreams = true }
  dependsOn(":ff-jni:nativeBuild")
  environment("FF_VMM_BIN", project(":ff-jni").projectDir.resolve("out/fg_vmm").absolutePath)
  environment("FF_VMM_LIBDIR", project(":ff-jni").projectDir.resolve("src/main/resources/io/vacco/ff").absolutePath)
}
