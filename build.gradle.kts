import java.io.FileInputStream
import java.util.Properties

plugins {
  alias(libs.plugins.kotlinMultiplatform).apply(false)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ktlint)
  alias(libs.plugins.dokka)
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
  version = versionProperties.get("projectVersion") as String
}

val javaVersion = JavaVersion.VERSION_17

tasks.withType<JavaCompile> {
  options.encoding = "UTF-8"
  sourceCompatibility = javaVersion.toString()
  targetCompatibility = javaVersion.toString()
}
