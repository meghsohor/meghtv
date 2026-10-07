plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

android {
    namespace = "dev.meghsohor.meghtv"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.meghsohor.meghtv"
        minSdk = 23
        targetSdk = 36
        versionCode = 5
        versionName = "1.3.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Release plus the owner's own channels from local/channels.json, which stays out of git. Never published.
        create("personal") {
            initWith(getByName("release"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

/** Copies local/channels.json, and nothing else from local/, into the personal build's assets. */
abstract class PersonalChannelsTask : DefaultTask() {
    @get:InputFile abstract val channels: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val file = channels.get().asFile
        // A file the app can't read would cost its channels, and their favourites, on the next refresh: fail here instead.
        val list = (groovy.json.JsonSlurper().parse(file) as? Map<*, *>)?.get("channels") as? List<*>
            ?: throw GradleException("$file: no \"channels\" list")
        list.forEachIndexed { i, entry ->
            val c = entry as? Map<*, *>
            fun strings(key: String) = (c?.get(key) as? List<*>)?.all { it is String } == true
            val urls = c?.get("urls") as? List<*>
            if (c?.get("id") !is String || c["name"] !is String || !strings("categories") || !strings("urls") || urls.isNullOrEmpty()) {
                throw GradleException("$file: channel ${i + 1} needs an id, a name, categories and at least one URL")
            }
        }
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        file.copyTo(out.resolve("personal_channels.json"))
    }
}

val personalChannels = tasks.register<PersonalChannelsTask>("personalChannels") {
    channels.set(rootProject.layout.projectDirectory.file("local/channels.json"))
}

androidComponents {
    onVariants(selector().withBuildType("personal")) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(personalChannels, PersonalChannelsTask::outputDir)
    }
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  debugImplementation(libs.androidx.compose.ui.tooling)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  implementation(libs.androidx.tv.foundation)
  implementation(libs.androidx.tv.material)

  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.exoplayer.hls)
  // One module per stream format the playlists use. No RTMP: ~380 KB for 6 channels.
  implementation(libs.androidx.media3.exoplayer.dash)
  implementation(libs.androidx.media3.exoplayer.smoothstreaming)
  implementation(libs.androidx.media3.exoplayer.rtsp)
  implementation(libs.androidx.media3.ui)

  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.paging)
  ksp(libs.androidx.room.compiler)
  implementation(libs.androidx.paging.runtime)
  implementation(libs.androidx.paging.compose)

  implementation(libs.okhttp)
}
