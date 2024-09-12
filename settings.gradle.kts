rootProject.name = "prosemirror-multiplatform"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
pluginManagement {
  repositories {
    google()
    gradlePluginPortal()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  versionCatalogs {
    create("libs") {
      from(files("libs.versions.toml"))
    }
  }
  repositories {
    google()
    mavenCentral()
  }
}

include("model")
include("state")
include("transform")
include("util")
include("collab")
include("history")
include("test-builder")
