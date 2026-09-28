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

  // Build-only CI (no KVM / caps / network): skip the privileged integration tests.
  if (project.hasProperty("skipPrivilegedTests") || System.getenv("FF_SKIP_PRIVILEGED_TESTS") == "1") {
    filter {
      for (c in listOf(
        "FgVmBootTest", "FgNetBootTest", "FgVmLifecycleTest", "FgStackRestTest",
        "FgStackRestartTest", "FgCowsayBootTest", "FgLogFlushTest", "FgImageExpandTest"
      )) {
        excludeTestsMatching("io.vacco.ff.$c")
      }
    }
  }
}
