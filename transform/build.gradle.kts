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
  val xcframeworkName = "transform"
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
      implementation(project(":util"))
    }
    commonTest.dependencies {
      implementation(project(":test-builder"))
      implementation(libs.kotlin.test)
      implementation(libs.test.assertk)
    }
  }
}

description = "prosemirror-transform"
ext.set("pomDescription", "ProseMirror document transformations")
ext.set("srcUrl", "https://github.com/atlassian-labs/prosemirror-kotlin/tree/main/transform/")
