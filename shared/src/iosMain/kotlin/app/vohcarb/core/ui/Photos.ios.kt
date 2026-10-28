package app.vohcarb.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

// TODO: PHPickerViewController and loading through Skia once the iOS app exists; until then the option is hidden.
actual val photoBackgroundsSupported: Boolean = false

@Composable
actual fun rememberPhotoPicker(onPicked: (path: String) -> Unit): () -> Unit = {}

actual suspend fun loadImage(path: String): ImageBitmap? = null
