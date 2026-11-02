import com.android.build.api.variant.FilterConfiguration
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose.compiler)
  alias(libs.plugins.kotlinx.serialization)
  alias(libs.plugins.room)
}

// KSP applied via buildscript classpath (see root build.gradle.kts) because the
// plugin marker artifact is rate-limited on Maven Central and not on Aliyun mirror.
pluginManager.apply("com.google.devtools.ksp")

android {
  namespace = "com.magen.player"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.magen.player"
    minSdk = 26
    targetSdk = 36
    // versionCode bumped from 216 to 220 so this is a proper upgrade over the
    // previous v1.0.0 release (which had versionCode 216 → arm64-v8a = 2162).
    // New arm64-v8a = 2202 > 2162, so Android accepts it as an update.
    versionCode = 220
    versionName = "5.5.1"

    vectorDrawables {
      useSupportLibrary = true
    }

    buildConfigField("String", "GIT_SHA", "\"${getCommitSha()}\"")
    buildConfigField("int", "GIT_COUNT", getCommitCount())
    // Enable update feature by default
    buildConfigField("boolean", "ENABLE_UPDATE_FEATURE", "false")
    buildConfigField("boolean", "SCOPED_STORAGE_ONLY", "false")
    buildConfigField("boolean", "IS_PREVIEW", "false")
  }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }

  splits {
    abi {
      isEnable = true
      reset()
      // Only ship ARM ABIs — Honor X6c NIC-LX2 (Helio G81) is arm64-v8a.
      // Dropping x86/x86_64 saves ~8-10 MB of emulator-only .so files (libmpv, mediainfo).
      include("arm64-v8a", "armeabi-v7a")
      isUniversalApk = true
    }
  }

  signingConfigs {
    create("release") {
      if (project.hasProperty("releaseKeyStore")) {
        storeFile = file(project.property("releaseKeyStore") as String)
        storePassword = project.property("releaseKeyStorePassword") as String
        keyAlias = project.property("releaseKeyAlias") as String
        keyPassword = project.property("releaseKeyPassword") as String
        // Enable v1+v2+v3 signing so APK installs on Android 14 (Honor X6c NIC-LX2)
        // which requires v2+ scheme.
        enableV1Signing = true
        enableV2Signing = true
        enableV3Signing = true
      }
    }
  }

  buildTypes {
    named("release") {
      if (project.hasProperty("releaseKeyStore")) {
        signingConfig = signingConfigs.getByName("release")
      }
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
      ndk {
        debugSymbolLevel = "none"
      }
    }

    create("preview") {
      initWith(getByName("release"))
      signingConfig = null
      applicationIdSuffix = ".preview"
      versionNameSuffix = "-${getCommitCount()}"
      buildConfigField("boolean", "IS_PREVIEW", "true")
    }

    named("debug") {
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-${getCommitCount()}"
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  testOptions {
    unitTests {
      isReturnDefaultValues = true
    }
  }

  buildFeatures {
    compose = true
    viewBinding = true
    buildConfig = true
    aidl = true
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
      excludes += "META-INF/DEPENDENCIES"
      excludes += "META-INF/LICENSE*"
      excludes += "META-INF/NOTICE*"
      excludes += "META-INF/*.kotlin_module"
      excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
    }
    jniLibs {
      useLegacyPackaging = true
    }
  }

  @Suppress("UnstableApiUsage")
  androidResources {
    generateLocaleConfig = true
  }
}

androidComponents {
  val abiCodes = mapOf(
    "armeabi-v7a" to 1,
    "arm64-v8a" to 2,
  )

  onVariants { variant ->
    variant.outputs.forEach { output ->
      val abi = output.filters
        .find { it.filterType == FilterConfiguration.FilterType.ABI }
        ?.identifier

      output.versionCode.set(
        (output.versionCode.orNull ?: 0) * 10 + (abiCodes[abi] ?: 0)
      )
    }
  }
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll(
      "-opt-in=com.google.accompanist.permissions.ExperimentalPermissionsApi",
      "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
    )
    jvmTarget.set(JvmTarget.JVM_17)
  }
}

composeCompiler {
  includeSourceInformation = true
}

room {
  schemaDirectory("$projectDir/schemas")
}

dependencies {
  implementation(libs.splashScreen)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.material3.android)
  implementation("com.google.android.material:material:1.13.0")
  implementation(libs.androidx.compose.material)
  implementation(libs.androidx.ui.tooling.preview)
  debugImplementation(libs.androidx.ui.tooling)
  implementation(libs.bundles.compose.navigation3)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.compose.constraintlayout)
  implementation("androidx.preference:preference-ktx:1.2.1")
  implementation("androidx.constraintlayout:constraintlayout:2.2.0")
  implementation(libs.androidx.material3.icons.extended)
  implementation(libs.androidx.compose.animation.graphics)
  implementation(libs.mediasession)
  implementation(libs.androidx.documentfile)
  implementation(libs.saveable)

  implementation(platform(libs.koin.bom))
  implementation(libs.bundles.koin)

  implementation(libs.seeker)
  implementation(libs.compose.prefs)

  implementation(libs.accompanist.permissions)

  implementation(libs.room.runtime)
  // KSP Room compiler — using add("ksp", ...) because KSP is applied via buildscript
  // classpath rather than the plugins DSL (see root build.gradle.kts).
  add("ksp", libs.room.compiler)
  implementation(libs.room.ktx)

  implementation(libs.kotlinx.immutable.collections)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.okhttp)

  implementation(libs.truetype.parser)
  implementation(libs.fsaf)
  implementation(libs.mediainfo.lib)
  implementation(libs.mpv.lib)
  implementation(libs.androidx.security.crypto)

  // Network protocol libraries
  implementation(libs.smbj)
  implementation(libs.commons.net)
  implementation(libs.sardine.android) {
    exclude(group = "xpp3", module = "xpp3")
  }
  implementation(libs.nanohttpd)
  implementation(libs.lazycolumnscrollbar)
  implementation(libs.reorderable)
  implementation(libs.compose.markdown)

  // Unit Testing
  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.turbine)
}

/* ---------------- Git helpers ---------------- */

fun getCommitCount(): String = "0"

fun getCommitSha(): String = "unknown"
