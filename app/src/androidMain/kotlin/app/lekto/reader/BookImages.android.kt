package app.lekto.reader

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Android decodes the derived asset from its file, so a large plate is never held twice. */
internal actual fun decodeImageBitmap(path: String): ImageBitmap? =
    runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()

/** Android decodes an in-memory cover; the bytes come from the derived store as one small read. */
internal actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? =
    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
