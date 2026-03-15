plugins {
    id("com.android.application")
}

val gitVersionName: String by rootProject.extra
val gitVersionCode: Int by rootProject.extra
val gitHash: String by rootProject.extra

android {
    namespace = "io.freewheel.bridge"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.freewheel.bridge"
        minSdk = 28
        targetSdk = 30
        versionCode = gitVersionCode
        versionName = gitVersionName
        buildConfigField("String", "GIT_HASH", "\"$gitHash\"")
    }

    buildFeatures {
        buildConfig = true
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

dependencies {
    implementation(project(":ucblib"))
}

