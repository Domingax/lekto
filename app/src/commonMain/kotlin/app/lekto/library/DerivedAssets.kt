package app.lekto.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A per-**Book** **Derived asset** while it is read, or `null` while it loads and
 * when the book has none (ADR-0005). The shelf never waits on a file: the read
 * runs off the main thread, and a surface shows the asset's absence until it
 * lands.
 *
 * [loader] is part of the key as well as [bookId], so swapping the composition
 * root's loader recomputes instead of keeping the previous store's answer.
 */
@Suppress("InjectDispatcher") // The read is the point: a store lookup must not block the shelf.
@Composable
internal fun <T> rememberDerivedAsset(bookId: String, loader: Any, load: suspend () -> T): State<T?> =
    produceState<T?>(initialValue = null, bookId, loader) {
        value = withContext(Dispatchers.Default) { load() }
    }
