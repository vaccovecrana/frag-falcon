plugins { id("io.vacco.oss.gitflow") version "1.8.3" apply(false) }

subprojects {

  apply(plugin = "io.vacco.oss.gitflow")

  group = "io.vacco.ff"
  version = "0.8.0"

  configure<io.vacco.oss.gitflow.GsPluginProfileExtension> {
    addClasspathHell()
  }

  configure<io.vacco.cphell.ChPluginExtension> {
    resourceExclusions.add("module-info.class")
  }

  project.ext.set("shax", "io.vacco.shax:shax:2.0.17.2")
  project.ext.set("slf4j", "org.slf4j:slf4j-api:2.0.17")

}
