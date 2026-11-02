package app.lekto.reader

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import java.io.File

/** Skia decodes the derived asset the desktop client already links through Compose. */
internal actual fun decodeImageBitmap(path: String): ImageBitmap? =
    runCatching { Image.makeFromEncoded(File(path).readBytes()).toComposeImageBitmap() }.getOrNull()

/** Skia decodes an in-memory cover the same way; the bytes are already small. */
internal actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
