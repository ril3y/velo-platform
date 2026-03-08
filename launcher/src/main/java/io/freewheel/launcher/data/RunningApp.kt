package io.freewheel.launcher.data

data class RunningApp(
    val packageName: String,
    val label: String,
    val memoryMb: Int,
    val pid: Int,
)
