pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    // Aliyun mirror first to avoid Maven Central 429 rate limiting
    maven(url = "https://maven.aliyun.com/repository/central")
    maven(url = "https://maven.aliyun.com/repository/public")
    // Explicit Maven Central endpoint (repo1.maven.org) — more reliable than mavenCentral()
    maven(url = "https://repo1.maven.org/maven2")
    gradlePluginPortal()
  }
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenLocal()
    // Aliyun mirror before Maven Central to avoid 429 rate limiting
    maven(url = "https://maven.aliyun.com/repository/central")
    maven(url = "https://maven.aliyun.com/repository/public")
    maven(url = "https://repo1.maven.org/maven2")
    maven(url = "https://magenplayer.github.io/MagenPlayer-libmpv")
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
