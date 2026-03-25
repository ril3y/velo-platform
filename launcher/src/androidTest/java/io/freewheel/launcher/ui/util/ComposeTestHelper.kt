package io.freewheel.launcher.ui.util

import android.graphics.Bitmap
import android.os.Environment
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import io.freewheel.launcher.ui.theme.VeloLauncherTheme
import java.io.File
import java.io.FileOutputStream

fun ComposeContentTestRule.setThemedContent(content: @Composable () -> Unit) {
    setContent {
        VeloLauncherTheme {
            content()
        }
    }
}

fun ComposeContentTestRule.screenshotRoot(name: String) {
    val bitmap = onRoot().captureToImage().asAndroidBitmap()
    val dir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        "velo-test-screenshots"
    )
    dir.mkdirs()
    val file = File(dir, "$name.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
}
