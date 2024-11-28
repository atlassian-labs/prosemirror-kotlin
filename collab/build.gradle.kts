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
  val xcframeworkName = project.name
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
      implementation(project(":model"))
      implementation(project(":state"))
      implementation(project(":transform"))
    }
    commonTest.dependencies {
      implementation(project(":history"))
      implementation(project(":test-builder"))
      implementation(libs.kotlin.test)
      implementation(libs.test.assertk)
    }
  }
}

description = "prosemirror-collab"
ext.set("pomDescription", "Collaborative editing for ProseMirror")
