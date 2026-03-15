plugins {
    id("com.android.library")
}

android {
    namespace = "io.freewheel.ucb"
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

dependencies {
    testImplementation("junit:junit:4.13.2")
}

// Task to build a standalone JAR for consumer apps (BikeArcade, FreeRide)
tasks.register<Jar>("buildClassesJar") {
    dependsOn("compileReleaseJavaWithJavac")
    archiveFileName.set("ucblib.jar")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    from(layout.buildDirectory.dir("intermediates/javac/release/compileReleaseJavaWithJavac/classes"))
    exclude("**/R.class", "**/R\$*.class", "**/BuildConfig.class")
}
