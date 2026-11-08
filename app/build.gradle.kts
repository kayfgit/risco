plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kayf.dedo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kayf.dedo"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Sideloaded for now, so the debug key is enough to install an optimized build.
            signingConfig = signingConfigs.getByName("debug")
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
    packaging {
        resources.excludes +=
            setOf("META-INF/*.version", "META-INF/**/*.kotlin_builtins", "kotlin/**", "DebugProbesKt.bin")
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
