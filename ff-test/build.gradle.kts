configure<io.vacco.oss.gitflow.GsPluginProfileExtension> {
  addJ8Spec()
}

tasks.withType<JacocoReport> {
  sourceSets(
      project(":ff-schema").sourceSets.main.get(),
      project(":ff-api").sourceSets.main.get(),
  )
}

dependencies {
  implementation(project(":ff-api"))
}
