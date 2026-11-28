import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// ---------------------------------------------------------------------------
// Versioning
//   versionName = <downix.versionBase>.<buildNumber>   e.g. 1.0.12
//   versionCode = <buildNumber>                        always grows, so Android accepts it as an update
// The CI workflow passes -PbuildNumber=<run number>. Local builds use buildNumber 0 (1.0.0, code 1).
// ---------------------------------------------------------------------------
val versionBase = providers.gradleProperty("downix.versionBase").orElse("1.0").get()
val buildNumber = providers.gradleProperty("buildNumber").map { it.toInt() }.orElse(0).get()

// GitHub "owner/repo" whose Releases hold the new APKs (used by the in-app update checker).
val updateRepo = providers.gradleProperty("updateRepo")
    .orElse(providers.gradleProperty("downix.updateRepo"))
    .orElse("")
    .get()

// ---------------------------------------------------------------------------
// Release signing. The SAME key must sign every build, otherwise Android refuses to
// update over the installed app. Source order:
//   1. environment (CI): DOWNIX_KEYSTORE_PATH, DOWNIX_KEYSTORE_PASSWORD, DOWNIX_KEY_ALIAS, DOWNIX_KEY_PASSWORD
//   2. signing-private/keystore.properties (local, never uploaded to GitHub)
// ---------------------------------------------------------------------------
val keystoreProps = Properties().apply {
    val file = rootProject.file("signing-private/keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(envName: String, propName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(propName)?.takeIf { it.isNotBlank() }

val releaseStoreFile: File? = signingValue("DOWNIX_KEYSTORE_PATH", "storeFile")?.let { path ->
    val candidate = File(path)
    if (candidate.isAbsolute) candidate else rootProject.file("signing-private/$path")
}?.takeIf { it.exists() }
val releaseStorePassword = signingValue("DOWNIX_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("DOWNIX_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("DOWNIX_KEY_PASSWORD", "keyPassword")
val hasReleaseSigning = releaseStoreFile != null &&
    releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null

android {
    namespace = "com.downix.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.downix.app"
        minSdk = 24
        targetSdk = 34
        versionCode = maxOf(buildNumber, 1)
        versionName = "$versionBase.$buildNumber"
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // Code shrinking (R8) is OFF: with it on, the release app closed itself right after opening.
            // Turn both back to true later only after testing a release build on the phone.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // One APK per CPU type instead of one big APK with every CPU's native code (the torrent library is large).
    // arm64-v8a = almost every phone from ~2017, armeabi-v7a = old 32-bit phones. The in-app updater picks the right one.
    // To drop 32-bit phones again: remove "armeabi-v7a" here AND the libtorrent4j-android-arm dependency below.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }
}

// A release APK signed with the wrong (or no) key would silently break updates: fail loudly instead.
gradle.taskGraph.whenReady {
    val buildsRelease = allTasks.any { it.name == "assembleRelease" || it.name == "bundleRelease" }
    if (buildsRelease && !hasReleaseSigning) {
        throw GradleException(
            "Release signing key not found. Put signing-private/ next to the project " +
                "(local build) or set the KEYSTORE_* secrets (GitHub Actions). See README.md."
        )
    }
}

dependencies {
    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.core:core-ktx:1.13.1")
    // Installs the Compose baseline profile on first launch (sideloaded APKs get no Play compile): smoother first scrolls
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")

    // Download engine
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Torrent engine: libtorrent4j (the libtorrent C++ library for Android) + its native code per CPU type (arm64 + 32-bit arm; x86_64 was removed to save APK size)
    val libtorrent4j = "2.1.0-35"
    implementation("org.libtorrent4j:libtorrent4j:$libtorrent4j")
    implementation("org.libtorrent4j:libtorrent4j-android-arm64:$libtorrent4j")
    implementation("org.libtorrent4j:libtorrent4j-android-arm:$libtorrent4j")
}
