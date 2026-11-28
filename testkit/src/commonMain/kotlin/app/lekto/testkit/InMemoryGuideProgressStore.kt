package app.lekto.testkit

import app.lekto.core.guide.GuideProgress
import app.lekto.core.guide.GuideProgressStore

/**
 * An in-memory [GuideProgressStore] for tests (ADR-0023's pattern; issue #277):
 * the app's composition and UI are proved without a file, while the JSON-backed
 * implementation is proved in `core/commonTest` (docs/development.md, "Test levels").
 * A store starts at [GuideProgress.NOT_SEEN] — exactly what a fresh install
 * holds — so a test that wants an existing reader passes [GuideProgress.SEEN].
 */
class InMemoryGuideProgressStore(progress: GuideProgress = GuideProgress.NOT_SEEN) : GuideProgressStore {

    private var stored: GuideProgress = progress

    override fun load(): GuideProgress = stored

    override fun save(progress: GuideProgress) {
        stored = progress
    }
}
