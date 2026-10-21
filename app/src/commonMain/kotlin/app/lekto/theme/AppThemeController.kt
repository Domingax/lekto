package app.lekto.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.lekto.core.theme.AppTheme
import app.lekto.core.theme.AppThemeStore

/**
 * The app's live **App theme** (ADR-0036): the paper the whole app paints itself
 * in, as Compose state, read and written through the device-local [AppThemeStore],
 * so a choice repaints every open screen at once with no relaunch and no toast, the
 * way a **Reader level** change recolours the reader's text (ADR-0030).
 *
 * The store is the truth; this holder only keeps the current choice observable and
 * writes each one back. It is device-local configuration, so it is never synced and
 * never reaches the vault.
 */
class AppThemeController(private val store: AppThemeStore) {

    /** The choice in effect; [AppTheme.SYSTEM] on a fresh install. */
    var theme by mutableStateOf(store.load())
        private set

    /** Applies [next] and stores it; **System** keeps following the platform. */
    fun choose(next: AppTheme) {
        theme = next
        store.save(next)
    }
}
