plugins {
    id("com.android.application") version "8.3.0" apply false
    id("com.android.library") version "8.3.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
    id("com.google.devtools.ksp") version "2.0.0-1.0.21" apply false
}

// ---------- Git-based versioning (shared across all modules) ----------
// Version comes from git tags: "v1.2.3" → versionName "1.2.3"
// versionCode is derived from commit count (always increasing)
// GIT_HASH is the short commit SHA for display/debugging

fun execGit(vararg args: String): String = try {
    val process = ProcessBuilder("git", *args)
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    process.inputStream.bufferedReader().readText().trim()
} catch (_: Exception) { "" }

val gitDescribe = execGit("describe", "--tags", "--always", "--dirty")
val gitHash = execGit("rev-parse", "--short=8", "HEAD").ifEmpty { "unknown" }
val gitCommitCount = execGit("rev-list", "--count", "HEAD").toIntOrNull() ?: 1

// Parse "v1.2.3" or "v1.2.3-5-gabcdef" from git describe
val gitVersionName: String = run {
    val tag = gitDescribe.removePrefix("v")
    val match = Regex("""^(\d+\.\d+\.\d+)""").find(tag)
    match?.groupValues?.get(1) ?: "0.0.0"
}

// Expose as extra properties for subprojects
extra["gitVersionName"] = gitVersionName
extra["gitVersionCode"] = gitCommitCount
extra["gitHash"] = gitHash
extra["gitDescribe"] = gitDescribe
