package app.lekto.testkit

import app.lekto.core.IdGenerator

/**
 * A deterministic [IdGenerator]: `0001`, `0002`, … so that records minted in a
 * test are readable and a failure always reproduces.
 *
 * @param prefix prepended to every id, for telling generated families apart.
 * @param start the first number handed out.
 */
class SequentialIdGenerator(private val prefix: String = "", start: Int = 1) : IdGenerator {

    private var next: Int = start

    override fun newId(): String = prefix + next++.toString().padStart(ID_WIDTH, '0')

    private companion object {
        /** Zero-padded width, so minted ids sort lexicographically. */
        const val ID_WIDTH = 4
    }
}
