import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.ktlint)
}

kotlin {
  // Java
  jvm {
    withJava()
    testRuns["test"].executionTask.configure {
      useJUnitPlatform()
    }
  }

  // iOS
  val xcframeworkName = "test-builder"
  val xcf = XCFramework(xcframeworkName)
  listOf(
    iosX64(),
    iosArm64(),
    iosSimulatorArm64(),
  ).forEach {
    it.binaries.framework {
      baseName = xcframeworkName
      binaryOption("bundleId", "com.atlassian.prosemirror.$xcframeworkName")
      xcf.add(this)
      isStatic = true
    }
  }

  sourceSets {
    commonMain.dependencies {
      implementation(libs.kotlinx.serialization.json)
      implementation(project(":model"))
      implementation(project(":transform"))
      implementation(project(":util"))
    }
    commonTest.dependencies {
    }
  }
}

description = "prosemirror-test-builder"
ext.set("pomDescription", "Document building utilities for writing tests")
ext.set("srcUrl", "https://github.com/atlassian-labs/prosemirror-kotlin/tree/main/test-builder/")
