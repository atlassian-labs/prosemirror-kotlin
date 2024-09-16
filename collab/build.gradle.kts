plugins {
  alias(libs.plugins.kotlin.jvm)
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

sourceSets {
  main {
    java {
      srcDir("src/commonMain")
    }
  }
  test {
    java {
      srcDir("src/commonTest")
    }
  }
}

dependencies {
  implementation(libs.kotlin.stdlib)
  implementation(project(":state"))
  implementation(project(":transform"))
  implementation(project(":model"))
  testImplementation(project(":test-builder"))
  testImplementation(kotlin("test"))
  testImplementation(libs.test.assertk)

}

description = "prosemirror-collab"

val javaVersion = JavaVersion.VERSION_17

tasks.withType<JavaCompile> {
  options.encoding = "UTF-8"
  sourceCompatibility = javaVersion.toString()
  targetCompatibility = javaVersion.toString()
}

tasks {

  jar {
    archiveBaseName.set("prosemirror-collab")
  }

  // This task is added by Gradle when we use java.withJavadocJar()
  named<Jar>("javadocJar") {
    from(dokkaJavadoc)
  }

  test {
    useJUnitPlatform()
  }

  publishing {
    publications {
      create<MavenPublication>("release") {
        from(project.components["java"])
        pom {
          packaging = "jar"
          name.set(project.name)
          description.set("Collaborative editing for ProseMirror")
          url.set("https://github.com/atlassian-labs/prosemirror-kotlin/tree/collab/")
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
    sign(publishing.publications["release"])
  }
}
