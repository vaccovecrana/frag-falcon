configure<io.vacco.oss.gitflow.GsPluginProfileExtension> {
  sharedLibrary(false, false)
  addJ8Spec()
}

val copyFfJni = tasks.register<Copy>("copyFfJni") {
  from("./ffrt")
  from("./fg_jni.so")
  into("./build/resources/main/io/vacco/ff")
}

tasks.processResources {
  dependsOn(copyFfJni)
}

val api by configurations

dependencies {
  api(project.ext.get("shax").toString())
}
