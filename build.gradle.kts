plugins { id("io.vacco.oss.gitflow") version "1.0.1" apply(false) }

subprojects {
  apply(plugin = "io.vacco.oss.gitflow")

  group = "io.vacco.ff"
  version = "0.5.10"

  configure<io.vacco.oss.gitflow.GsPluginProfileExtension> {
    addClasspathHell()
  }

  configure<io.vacco.cphell.ChPluginExtension> {
    resourceExclusions.add("module-info.class")
  }
}
