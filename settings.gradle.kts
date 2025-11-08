pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

include(
  "ff-native", "ff-archive", "ff-docker"
  // "ff-api", // "ff-ui",
  // "ff-app"
)

// project(":ff-app").name = "flc-${System.getProperty("os.name").lowercase()}-${System.getProperty("os.arch")}"
