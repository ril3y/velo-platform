plugins {
    id("com.android.library")
}

android {
    namespace = "io.freewheel.fit"
    compileSdk = 34

    defaultConfig {
        minSdk = 28
    }

    buildFeatures {
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }
}

// Task to build a standalone JAR for consumer apps (BikeArcade, FreeRide)
tasks.register<Jar>("buildClassesJar") {
    dependsOn("compileReleaseJavaWithJavac")
    archiveFileName.set("velofit.jar")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    from(layout.buildDirectory.dir("intermediates/javac/release/compileReleaseJavaWithJavac/classes"))
    exclude("**/R.class", "**/R\$*.class", "**/BuildConfig.class")
    manifest {
        val gitVersionName: String by rootProject.extra
        val gitHash: String by rootProject.extra
        attributes(
            "Implementation-Title" to "velofit",
            "Implementation-Version" to gitVersionName,
            "Git-Hash" to gitHash,
        )
    }
}
