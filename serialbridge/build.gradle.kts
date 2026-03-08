plugins {
    id("com.android.application")
}

android {
    namespace = "io.freewheel.bridge"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.freewheel.bridge"
        minSdk = 28
        targetSdk = 30
        versionCode = 3
        versionName = "3.0"
    }

    buildFeatures {
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    packagingOptions {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    // No signingConfig — we sign post-build with platform key via sign.sh
}
