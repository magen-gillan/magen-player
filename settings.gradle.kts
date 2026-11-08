pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    maven(url = "https://maven.aliyun.com/repository/public")
    maven(url = "https://maven.aliyun.com/repository/google")
    maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
    gradlePluginPortal()
  }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    // Aliyun mirrors first: they mirror Maven Central + Google and are not rate-limited
    // from this environment (repo.maven.apache.org returns 429 under load).
    maven(url = "https://maven.aliyun.com/repository/public")
    maven(url = "https://maven.aliyun.com/repository/google")
    google()
    mavenLocal()
    mavenCentral()
    maven(url = "https://mpvrex.github.io/mpvRex-libmpv")
    maven(url = "https://www.jitpack.io") {
      content {
        // Only use JitPack for specific dependencies to avoid unnecessary checks
        includeGroup("com.github.sfsakhawat999")
        includeGroup("io.github.abdallahmehiz")
        includeGroup("com.github.abdallahmehiz")
        includeGroup("com.github.K1rakishou")
        includeGroup("com.github.marlboro-advance")
        includeGroup("com.github.thegrizzlylabs")
        includeGroup("com.github.nanihadesuka")
        includeGroup("com.github.jeziellago")
      }
    }
  }
}

rootProject.name = "MagenPlayer"
include(":app")
