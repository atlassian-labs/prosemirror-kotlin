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
  val xcframeworkName = "util"
  val xcf = XCFramework(xcframeworkName)
  listOf(
    iosX64(),
    iosArm64(),
    iosSimulatorArm64(),
  ).forEach {
    it.binaries.framework {
      baseName = xcframeworkName
      binaryOption("bundleId", "com.atlassian.prosemirror.$xcframeworkName")
      binaryOption("bundleVersion", "${project.version}")
      xcf.add(this)
      isStatic = true
    }
  }

  sourceSets {
    commonMain.dependencies {
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.test.assertk)
    }
  }
}

description = "prosemirror-util"
ext.set("pomDescription", "ProseMirror utilities")
ext.set("srcUrl", "https://github.com/atlassian-labs/prosemirror-kotlin/tree/main/util/")
