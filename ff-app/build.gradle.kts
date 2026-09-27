plugins {
  application
}

dependencies {
  implementation(project(":ff-api"))
}

application {
  mainClass.set("io.vacco.ff.FgMain")
}
