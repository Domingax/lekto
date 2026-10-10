package app.lekto.core.vault

import java.io.File
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * The [VaultFileSystem] backed by a real directory, used by both clients.
 *
 * Desktop and Android share it: the app-private root differs — the OS app-data
 * directory versus `Context.filesDir` — but the storage is the same
 * `java.nio.file` API on both, so the vault behaves identically (ADR-0007's
 * update records that `jvmSharedMain` is compiled into both targets).
 *
 * Each write lands in a temporary sibling and is moved into place with an
 * atomic replace, so a reader sees the old file or the new one, never a
 * half-written mix (ADR-0003).
 */
class JvmVaultFileSystem(root: File) : VaultFileSystem {

    /** The root as an absolute, normalised path; the anchor every other path is resolved against. */
    private val rootPath: Path = root.toPath().toAbsolutePath().normalize()

    override fun read(path: String): ByteArray? {
        val file = resolve(path)
        if (!Files.isRegularFile(file)) return null
        return try {
            Files.readAllBytes(file)
        } catch (_: NoSuchFileException) {
            // The path was removed between the check and the read; treat it as absent.
            null
        }
    }

    override fun writeAtomically(path: String, bytes: ByteArray) {
        val target = resolve(path)
        val parent = target.parent ?: rootPath
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, ".${target.fileName}.", TEMP_SUFFIX)
        try {
            Files.write(temporary, bytes)
            // The temporary is a sibling, so source and target share a filesystem
            // and the move is atomic; a cross-filesystem move would throw
            // AtomicMoveNotSupportedException instead of falling back to a copy.
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    override fun delete(path: String) {
        // An absent path is already deleted, not an error (VaultFileSystem); a
        // present path that will not delete — a non-empty directory, say —
        // surfaces its IOException.
        Files.deleteIfExists(resolve(path))
    }

    override fun listFiles(): List<String> {
        if (!Files.isDirectory(rootPath)) return emptyList()
        return Files.walk(rootPath).use { tree ->
            tree.filter { Files.isRegularFile(it) }
                .map { rootPath.relativize(it).joinToString("/") }
                .toList()
        }
    }

    override fun storedPath(path: String): String = resolve(path).toString()

    /**
     * Resolves [path] under the root, refusing a path that normalises outside it.
     *
     * The guard is lexical: it folds `..` but does not follow symlinks, so it can
     * anchor a path that does not exist yet — every write's target — which
     * `Path.toRealPath` cannot. The root is app-private and `VaultPaths` rejects
     * `..` and unsafe segments before the bytes reach here, so containment against
     * untrusted ids is what the guard has to hold (issue #126).
     */
    private fun resolve(path: String): Path {
        val candidate = rootPath.resolve(path).normalize()
        require(candidate.startsWith(rootPath)) { "'$path' escapes the vault root" }
        return candidate
    }

    private companion object {
        /** The suffix on a write's temporary neighbour; the move leaves none behind. */
        const val TEMP_SUFFIX: String = ".tmp"
    }
}
