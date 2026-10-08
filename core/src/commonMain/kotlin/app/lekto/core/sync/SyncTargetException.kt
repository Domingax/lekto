package app.lekto.core.sync

/**
 * A synchronisation exchange failed in a way the caller may recover from: the
 * target was unreachable, answered an error, or returned something a driver
 * could not interpret. It is the honest failure ADR-0009 wants — a run that
 * cannot complete aborts so the next one retries, rather than being reported as
 * a successful empty listing, which would lose records silently.
 *
 * A **conflicting** write is not this: it is a [WriteOutcome.Conflicted], a value
 * the engine settles, not an error. [message] describes the failure and never
 * carries a credential.
 */
class SyncTargetException(message: String, cause: Throwable? = null) : Exception(message, cause)
