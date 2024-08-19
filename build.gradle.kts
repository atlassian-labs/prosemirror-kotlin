import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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

dependencies {
  implementation(libs.kotlin.stdlib)
  implementation(libs.jsoup)
  implementation(libs.kotlinx.serialization.json)
  testImplementation(kotlin("test"))
  testImplementation(libs.test.assertj)
}

group = "com.atlassian.prosemirror"
version = "0.0.1"
description = "prosemirror"

val javaVersion = JavaVersion.VERSION_17

tasks.withType<JavaCompile> {
  options.encoding = "UTF-8"
  sourceCompatibility = javaVersion.toString()
  targetCompatibility = javaVersion.toString()
}

tasks {

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
          description.set("A toolkit for building rich-text editors with Kotlin")
          url.set("https://github.com/atlassian-labs/prosemirror-kotlin")
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
