rootProject.name = "prosemirror"

dependencyResolutionManagement {
  versionCatalogs {
    create("libs") {
      from(files("libs.versions.toml"))
    }
  }
}
include("model")
include("state")
include("transform")
include("util")
include("collab")
include("history")
include("test-builder")
