package app.lekto.guide

import app.lekto.core.guide.GuideProgress
import app.lekto.testkit.InMemoryGuideProgressStore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The first-launch guide's live progress (issue #277): it reads the stored fact at
 * startup, `markSeen` writes through, and the store is the truth — a "restart", a
 * fresh controller over the same store, does not show the guide again.
 */
class GuideProgressControllerTest {

    @Test
    fun aFreshStoreHasNotSeenTheGuide() {
        val controller = GuideProgressController(InMemoryGuideProgressStore())

        assertFalse(controller.seen)
    }

    @Test
    fun aStoredSeenGuideIsLoadedAtStartup() {
        val controller = GuideProgressController(InMemoryGuideProgressStore(GuideProgress.SEEN))

        assertTrue(controller.seen)
    }

    @Test
    fun markingItSeenWritesThrough() {
        val store = InMemoryGuideProgressStore()
        val controller = GuideProgressController(store)

        controller.markSeen()

        assertTrue(controller.seen)
        assertTrue(store.load().seen)
    }

    @Test
    fun aRestartDoesNotShowTheGuideAgain() {
        val store = InMemoryGuideProgressStore()
        GuideProgressController(store).markSeen()

        assertTrue(GuideProgressController(store).seen)
    }
}
