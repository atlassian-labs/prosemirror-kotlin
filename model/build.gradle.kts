import java.net.URL
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.kotlin.atomicfu)
  alias(libs.plugins.ktlint)
  id("kotlinx-serialization")
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
  val xcframeworkName = "model"
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
      implementation(libs.stately.concurrent.collections)
      implementation(project(":util"))
      api(libs.ksoup)
    }
    commonTest.dependencies {
      implementation(project(":test-builder"))
      implementation(libs.kotlin.test)
      implementation(libs.test.assertk)
    }
  }
}

description = "prosemirror-model"
ext.set("pomDescription", "ProseMirror document model")
ext.set("srcUrl", "https://github.com/atlassian-labs/prosemirror-kotlin/tree/main/model/")
