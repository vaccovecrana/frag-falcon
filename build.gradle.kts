plugins { id("io.vacco.oss.gitflow") version libs.versions.gitflowPl apply (false) }

subprojects {
  apply(plugin = "io.vacco.oss.gitflow")

  group = "io.vacco.ff"
  version = "1.0.0"

  configure<io.vacco.oss.gitflow.GsPluginProfileExtension> {
    addClasspathHell()
  }

  configure<io.vacco.cphell.ChPluginExtension> {
    resourceExclusions.add("module-info.class")
    resourceExclusions.add("LICENSE")
  }
}
