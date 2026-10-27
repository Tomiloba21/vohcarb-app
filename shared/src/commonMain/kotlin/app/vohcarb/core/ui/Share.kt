package app.vohcarb.core.ui

import androidx.compose.runtime.Composable

/** Opens the system share sheet with [text] (e.g. a group invite). */
@Composable
expect fun rememberShareText(): (text: String) -> Unit
