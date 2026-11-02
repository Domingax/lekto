package app.lekto.testkit

import app.lekto.core.vault.VaultFileSystem

/**
 * A file system that fails when a `.json` file is read, which is how a test
 * proves that listing a shelf reads only the parsed text's tiny derived entries
 * and never decodes the book itself (issues #196, #260). Everything else is
 * delegated unchanged.
 */
class TextBlindFileSystem(private val backing: VaultFileSystem) : VaultFileSystem by backing {
    override fun read(path: String): ByteArray? {
        check(!path.endsWith(".json")) { "listing decoded the parsed text: $path" }
        return backing.read(path)
    }
}
