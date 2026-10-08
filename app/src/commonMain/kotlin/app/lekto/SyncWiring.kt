package app.lekto

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.lekto.settings.SyncController
import kotlinx.coroutines.CoroutineScope

/**
 * Builds the sync controller where both a driver ([AppEnvironment.sync]) and a
 * **Secret store** are wired, so [App] itself stays a flat wiring function. A
 * platform that supplies neither gets `null`, and the sync section says it is
 * unavailable rather than offering a dead action.
 */
@Composable
internal fun rememberSyncController(environment: AppEnvironment, scope: CoroutineScope): SyncController? =
    remember(environment.sync, environment.secrets, environment.dispatcher, scope) {
        val services = environment.sync
        val secrets = environment.secrets
        if (services != null && secrets != null) {
            SyncController(services, secrets, environment.dispatcher, scope)
        } else {
            null
        }
    }
