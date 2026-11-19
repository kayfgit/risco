plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kayf.risco"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kayf.risco"
        minSdk = 30
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"
    }

    // The release key, from the environment: GitHub's secrets when a release is built, or .env
    // locally. Every release must be signed with it, or phones refuse the update. Without it
    // (pull requests, install-local.ps1), release builds fall back to this machine's debug key.
    val releaseKey = System.getenv("RISCO_KEYSTORE")?.takeIf { file(it).exists() }
    signingConfigs {
        if (releaseKey != null) {
            create("release") {
                storeFile = file(releaseKey)
                storePassword = System.getenv("RISCO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RISCO_KEY_ALIAS") ?: "risco"
                keyPassword = System.getenv("RISCO_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
        buildConfig = false
    }
    // gradlew lint. Every warning fails the build, so nothing piles up unread.
    lint {
        warningsAsErrors = true
        abortOnError = true
        // Still reported, but doesn't fail: raising targetSdk changes how the app behaves
        // (35 forces edge-to-edge), so it's done on purpose and tested on a phone, not to quiet lint.
        informational += "OldTargetApi"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    packaging {
        resources.excludes +=
            setOf("META-INF/*.version", "META-INF/**/*.kotlin_builtins", "kotlin/**", "DebugProbesKt.bin")
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // The library Android's android.icu comes from, so alphabet tests run on the PC.
    testImplementation("com.ibm.icu:icu4j:76.1")
    // Runs Android on the PC, so a test can open home the way the phone does.
    testImplementation("org.robolectric:robolectric:4.14.1")
}
