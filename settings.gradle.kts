pluginManagement {
  repositories {
    // Fast mirrors that don't rate-limit this environment (repo.maven.apache.org 429s,
    // maven.aliyun.com intermittently 502s). JitPack-hosted groups are excluded: the
    // mirrors proxy their POMs but not their AARs, which breaks resolution.
    maven(url = "https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") {
      content { excludeGroupByRegex("com\\.github..*"); excludeGroupByRegex("io\\.github..*") }
    }
    maven(url = "https://repo.huaweicloud.com/repository/maven/") {
      content { excludeGroupByRegex("com\\.github..*"); excludeGroupByRegex("io\\.github..*") }
    }
    maven(url = "https://maven.aliyun.com/repository/public") {
      content { excludeGroupByRegex("com\\.github..*"); excludeGroupByRegex("io\\.github..*") }
    }
    maven(url = "https://maven.aliyun.com/repository/google")
    maven(url = "https://maven.aliyun.com/repository/gradle-plugin") {
      content { excludeGroupByRegex("com\\.github..*"); excludeGroupByRegex("io\\.github..*") }
    }
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    // Mirrors first: they proxy Maven Central + Google and are not rate-limited
    // from this environment (repo.maven.apache.org returns 429 under load).
    // JitPack-hosted groups excluded: mirrors serve their POMs but 404 their AARs.
    maven(url = "https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") {
      content { excludeGroupByRegex("com\\.github..*"); excludeGroupByRegex("io\\.github..*") }
    }
    maven(url = "https://repo.huaweicloud.com/repository/maven/") {
      content { excludeGroupByRegex("com\\.github..*"); excludeGroupByRegex("io\\.github..*") }
    }
    maven(url = "https://maven.aliyun.com/repository/public") {
      content { excludeGroupByRegex("com\\.github..*"); excludeGroupByRegex("io\\.github..*") }
    }
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
