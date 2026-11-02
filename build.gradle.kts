// Top-level build file where you can add configuration options common to all sub-projects/modules.

// KSP loaded via buildscript classpath because the plugin marker artifact
// (com.google.devtools.ksp.gradle.plugin) is sometimes rate-limited on Maven Central (429)
// and is not mirrored on Aliyun. The actual library artifact (symbol-processing-gradle-plugin)
// IS available on Aliyun, so we load it directly here.
buildscript {
  repositories {
    google()
    maven(url = "https://maven.aliyun.com/repository/central")
    maven(url = "https://maven.aliyun.com/repository/public")
    maven(url = "https://repo1.maven.org/maven2")
    mavenCentral()
  }
  dependencies {
    classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.9")
  }
}

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose.compiler) apply false
  alias(libs.plugins.kotlinx.serialization) apply false
  alias(libs.plugins.room) apply false
}
