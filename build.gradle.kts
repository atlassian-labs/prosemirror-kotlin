import java.io.FileInputStream
import java.net.URL
import java.util.Properties

plugins {
  alias(libs.plugins.kotlinMultiplatform).apply(false)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ktlint)
  alias(libs.plugins.dokka)
  id("maven-publish")
  id("signing")
}

repositories {
  mavenCentral()
}

java {
  withSourcesJar()
  withJavadocJar()
}

dependencies {
  implementation(libs.kotlin.stdlib)
}

val versionProperties = Properties()
versionProperties.load(FileInputStream("version.properties"))

allprojects {
  group = "com.atlassian.prosemirror"
  version = versionProperties["projectVersion"] as String
}

subprojects {
  apply(plugin = "maven-publish")
  apply(plugin = "signing")
  apply(plugin = "org.jetbrains.dokka") // TODO: use alias

  afterEvaluate { // afterEvaluate so that project.ext values will be available
    project.tasks.dokkaHtml {
      dokkaSourceSets {
        val urlPrefix = project.ext.get("srcUrl") as String
        val commonMain by getting {
          sourceLink {
            // Unix based directory relative path to the root of the project (where you execute gradle respectively).
            localDirectory.set(file("src/commonMain/kotlin"))

            // URL showing where the source code can be accessed through the web browser
            remoteUrl.set(URL("${urlPrefix}src/main/src/commonMain/kotlin"))

            // Suffix which is used to append the line number to the URL. Use #L for GitHub
            remoteLineSuffix.set("#lines-")
          }
        }

        val jvmMain by getting {
          sourceLink {
            // Unix based directory relative path to the root of the project (where you execute gradle respectively).
            localDirectory.set(file("src/jvmMain/kotlin"))

            // URL showing where the source code can be accessed through the web browser
            remoteUrl.set(URL("${urlPrefix}src/main/src/jvmMain/kotlin"))

            // Suffix which is used to append the line number to the URL. Use #L for GitHub
            remoteLineSuffix.set("#lines-")
          }
        }

        val nativeMain by getting {
          sourceLink {
            // Unix based directory relative path to the root of the project (where you execute gradle respectively).
            localDirectory.set(file("src/nativeMain/kotlin"))

            // URL showing where the source code can be accessed through the web browser
            remoteUrl.set(URL("${urlPrefix}src/main/src/nativeMain/kotlin"))

            // Suffix which is used to append the line number to the URL. Use #L for GitHub
            remoteLineSuffix.set("#lines-")
          }
        }
      }
    }

    publishing {
      publications {
        publications.withType<MavenPublication> {
          pom {
            name.set(project.name)
            description.set(project.ext.get("pomDescription") as String)
            url.set(project.ext.get("srcUrl") as String)

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
      setRequired { System.getenv("SIGNING_KEY") == "" } // only sign if the key is available (usually on CI)
      useInMemoryPgpKeys(
        System.getenv("SIGNING_KEY"),
        System.getenv("SIGNING_PASSWORD"),
      )
      sign(publishing.publications)
    }
  }
}

val javaVersion = JavaVersion.VERSION_17

tasks.withType<JavaCompile> {
  options.encoding = "UTF-8"
  sourceCompatibility = javaVersion.toString()
  targetCompatibility = javaVersion.toString()
}
