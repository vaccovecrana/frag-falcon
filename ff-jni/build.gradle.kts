configure<io.vacco.oss.gitflow.GsPluginProfileExtension> {
  sharedLibrary(false, false)
}

val copyFfJni = tasks.register<Copy>("copyFfJni") {
  from("./out/fg_jni.so")
  into("./build/resources/main/io/vacco/ff")
}

tasks.processResources {
  dependsOn(copyFfJni)
}
