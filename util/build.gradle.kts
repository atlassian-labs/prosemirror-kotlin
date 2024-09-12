import java.net.URL
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.ktlint)
  alias(libs.plugins.dokka)
  id("maven-publish")
  id("signing")
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

  tasks.dokkaHtml {
    dokkaSourceSets {
      val commonMain by getting {
        sourceLink {
          // Unix based directory relative path to the root of the project (where you execute gradle respectively).
          localDirectory.set(file("src/commonMain/kotlin"))

          // URL showing where the source code can be accessed through the web browser
          remoteUrl.set(URL("https://github.com/atlassian-labs/prosemirror-kotlin/util/src/main/src/commonMain/kotlin"))

          // Suffix which is used to append the line number to the URL. Use #L for GitHub
          remoteLineSuffix.set("#lines-")
        }
      }

      val jvmMain by getting {
        sourceLink {
          // Unix based directory relative path to the root of the project (where you execute gradle respectively).
          localDirectory.set(file("src/jvmMain/kotlin"))

          // URL showing where the source code can be accessed through the web browser
          remoteUrl.set(URL("https://github.com/atlassian-labs/prosemirror-kotlin/util/src/main/src/jvmMain/kotlin"))

          // Suffix which is used to append the line number to the URL. Use #L for GitHub
          remoteLineSuffix.set("#lines-")
        }
      }

      val nativeMain by getting {
        sourceLink {
          // Unix based directory relative path to the root of the project (where you execute gradle respectively).
          localDirectory.set(file("src/nativeMain/kotlin"))

          // URL showing where the source code can be accessed through the web browser
          remoteUrl.set(URL("https://github.com/atlassian-labs/prosemirror-kotlin/util/src/main/src/nativeMain/kotlin/"))

          // Suffix which is used to append the line number to the URL. Use #L for GitHub
          remoteLineSuffix.set("#lines-")
        }
      }
    }
  }
}

description = "prosemirror-util"

publishing {
  publications {
    publications.withType<MavenPublication> {
      pom {
        name.set(project.name)
        description.set("ProseMirror utilities")
        url.set("https://github.com/atlassian-labs/prosemirror-kotlin/tree/util/")

        scm {
          connection.set("git@github.com:atlassian-labs/prosemirror-kotlin.git")
          url.set("https://github.com/atlassian-labs/prosemirror-kotlin.git")
        }
        developers {
          developer {
            id.set("dmarques")
            name.set("Douglas Marques")
            email.set("dmarques@atlassian.com")
          }
          developer {
            id.set("achernykh")
            name.set("Aleksei Chernykh")
            email.set("achernykh@atlassian.com")
          }
        }
        licenses {
          license {
            name.set("Apache License 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0")
            distribution.set("repo")
          }
        }
      }
    }
  }

  repositories {
    maven {
      url = uri("https://packages.atlassian.com/maven-central")
      credentials {
        username = System.getenv("ARTIFACTORY_USERNAME")
        password = System.getenv("ARTIFACTORY_API_KEY")
      }
    }
  }
}

signing {
  useInMemoryPgpKeys(
    System.getenv("SIGNING_KEY"),
    System.getenv("SIGNING_PASSWORD"),
  )
  sign(publishing.publications)
}
