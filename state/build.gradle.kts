import java.net.URL
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
  val xcframeworkName = "state"
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
      implementation(libs.kotlin.datetime)
      implementation(project(":model"))
      implementation(project(":transform"))
      implementation(project(":util"))
    }
    commonTest.dependencies {
      implementation(project(":test-builder"))
      implementation(libs.kotlin.test)
      implementation(libs.test.assertk)
    }
  }
}

description = "prosemirror-state"
ext.set("pomDescription", "ProseMirror editor state")
ext.set("pomUrl", "https://github.com/atlassian-labs/prosemirror-kotlin/tree/state/")
ext.set("dokkaUrlPrefix", "https://github.com/atlassian-labs/prosemirror-kotlin/state/")
