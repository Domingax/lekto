package app.lekto.testkit

import app.lekto.core.book.SampleDismissed
import app.lekto.core.book.SampleDismissedStore

/**
 * An in-memory [SampleDismissedStore] for tests (issue #278; ADR-0023's
 * pattern): the sample book's removal is proved without a file, while the
 * JSON-backed implementation is proved in `core/commonTest` and `core/jvmTest`
 * (docs/development.md, "Test levels"). A store starts at
 * [SampleDismissed.NONE] — exactly what a fresh install holds.
 */
class InMemorySampleDismissedStore(dismissed: SampleDismissed = SampleDismissed.NONE) : SampleDismissedStore {

    private var stored: SampleDismissed = dismissed

    override fun load(): SampleDismissed = stored

    override fun save(dismissed: SampleDismissed) {
        stored = dismissed
    }
}
