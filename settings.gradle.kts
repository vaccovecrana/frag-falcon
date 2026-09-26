pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

include("ff-jni", "ff-api", "ff-ui", "ff-app", "ff-test")
project(":ff-app").name = "flc-${System.getProperty("os.name").lowercase()}-${System.getProperty("os.arch")}"
