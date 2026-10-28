package app.vohcarb.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/** Whether the background can be the user's photo on this platform yet. */
expect val photoBackgroundsSupported: Boolean

/**
 * Opens the system photo picker. The chosen photo is copied (scaled down) into the app's own files, and
 * [onPicked] gets that copy's path.
 */
@Composable
expect fun rememberPhotoPicker(onPicked: (path: String) -> Unit): () -> Unit

/** Loads a photo saved by the picker; null if it's gone. */
expect suspend fun loadImage(path: String): ImageBitmap?
