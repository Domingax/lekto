package app.lekto.core.vault

import java.io.File
import java.io.IOException

/**
 * The [VaultFileSystem] backed by a real directory, used by both clients.
 *
 * Desktop and Android share it: the app-private root differs — the OS app-data
 * directory versus `Context.filesDir` — but the storage is the same Java file
 * API, so the vault behaves identically on both (ADR-0007's update records that
 * Android consumes the JVM artifact until `core` declares an Android target).
 * It is built on `java.io.File`, not `java.nio.file`, because the latter starts
 * at Android API 26 and the app's `minSdk` is lower.
 *
 * Each write lands in a hidden sibling (`.name.tmp`) and is renamed into place,
 * so a reader sees the old file or the new one, never a half-written mix
 * (ADR-0003).
 */
class JvmVaultFileSystem(private val root: File) : VaultFileSystem {

    override fun read(path: String): ByteArray? = resolve(path).takeIf { it.isFile }?.readBytes()

    override fun writeAtomically(path: String, bytes: ByteArray) {
        val target = resolve(path)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, ".${target.name}.tmp")
        temp.writeBytes(bytes)
        if (temp.renameTo(target)) return
        // Windows refuses to rename over an existing file; removing it first
        // risks a moment with no file, but never a partially written one.
        if (target.delete() && temp.renameTo(target)) return
        throw IOException("could not atomically replace '${target.path}'")
    }

    override fun delete(path: String) {
        val file = resolve(path)
        // An absent path is already deleted, not an error (VaultFileSystem);
        // a present path that will not delete is a real failure.
        if (!file.delete() && file.exists()) {
            throw IOException("could not delete '${file.path}'")
        }
    }

    override fun listFiles(): List<String> = root.walkTopDown()
        .filter { it.isFile }
        .map { it.relativeTo(root).invariantSeparatorsPath }
        .toList()

    /** Resolves [path] under the root and refuses anything that escapes it. */
    private fun resolve(path: String): File {
        val base = root.canonicalFile
        val candidate = File(root, path).canonicalFile
        require(candidate == base || candidate.path.startsWith(base.path + File.separator)) {
            "'$path' escapes the vault root"
        }
        return candidate
    }
}
