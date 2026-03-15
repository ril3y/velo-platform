import java.util.Properties

plugins {
    id("com.android.application")
}

val versionFile = file("version.properties")
val versionProps = Properties().apply {
    if (versionFile.exists()) load(versionFile.inputStream())
}
val vMajor = (versionProps["VERSION_MAJOR"] as? String)?.toIntOrNull() ?: 3
val vMinor = (versionProps["VERSION_MINOR"] as? String)?.toIntOrNull() ?: 0
val vPatch = (versionProps["VERSION_PATCH"] as? String)?.toIntOrNull() ?: 0
val vBuild = (versionProps["VERSION_BUILD"] as? String)?.toIntOrNull() ?: 3

android {
    namespace = "io.freewheel.bridge"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.freewheel.bridge"
        minSdk = 28
        targetSdk = 30
        versionCode = vBuild
        versionName = "$vMajor.$vMinor.$vPatch"
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

tasks.register("bumpBuildNumber") {
    doLast {
        val next = vBuild + 1
        versionFile.writeText(
            "VERSION_MAJOR=$vMajor\nVERSION_MINOR=$vMinor\nVERSION_PATCH=$vPatch\nVERSION_BUILD=$next\n"
        )
        println("Version bumped: $vMajor.$vMinor.$vPatch build $vBuild -> $next")
    }
}

afterEvaluate {
    tasks.matching { it.name.startsWith("assemble") || it.name.startsWith("bundle") }.configureEach {
        finalizedBy("bumpBuildNumber")
    }
}
