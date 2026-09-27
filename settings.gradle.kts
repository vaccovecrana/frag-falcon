pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

include("ff-jni", "ff-api", "ff-app", "ff-test")

project(":ff-app").name = "flc"