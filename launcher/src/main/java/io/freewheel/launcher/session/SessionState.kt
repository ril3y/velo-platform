package io.freewheel.launcher.session

data class SessionState(
    val active: Boolean = false,
    val ownerPackage: String? = null,
    val ownerLabel: String? = null,
    val startTime: Long = 0,
    val elapsedSeconds: Int = 0,
)
