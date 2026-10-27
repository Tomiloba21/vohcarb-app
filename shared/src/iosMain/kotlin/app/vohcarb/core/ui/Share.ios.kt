package app.vohcarb.core.ui

import androidx.compose.runtime.Composable
import platform.UIKit.UIPasteboard

// TODO: a UIActivityViewController share sheet once the iOS app exists; copying is the simple stand-in.
@Composable
actual fun rememberShareText(): (text: String) -> Unit = { text -> UIPasteboard.generalPasteboard.string = text }
