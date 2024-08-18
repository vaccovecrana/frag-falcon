configure<io.vacco.oss.gitflow.GsPluginProfileExtension> {
  addJ8Spec()
}

tasks.withType<JacocoReport> {
  sourceSets(project(":ff-api").sourceSets.main.get(),)
}

tasks.jacocoTestReport {
  classDirectories.setFrom(files(classDirectories.files.map {
    fileTree(it) {
      exclude("io/vacco/ff/firecracker/**")
    }
  }))
}

dependencies {
  implementation(project(":ff-api"))
}
