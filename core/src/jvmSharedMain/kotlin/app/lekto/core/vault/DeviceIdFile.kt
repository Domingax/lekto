package app.lekto.core.vault

import java.io.File
import java.util.UUID

/**
 * The installation's stable [DeviceId], persisted in one app-private file
 * (ADR-0003, which stamps every record with it).
 *
 * It lives on the JVM side because persisting it is platform-backed like the
 * vault itself; Android and desktop share the implementation and differ only in
 * the file they point it at. The first read mints an id and writes it, so every
 * later read — and every record — sees the same one.
 */
class DeviceIdFile(private val file: File) {

    /** The persisted device id, creating and storing one on first use. */
    fun get(): DeviceId {
        val existing = file.takeIf { it.isFile }?.readText()?.trim().orEmpty()
        if (existing.isNotEmpty()) return DeviceId(existing)
        val minted = DeviceId(UUID.randomUUID().toString())
        file.parentFile?.mkdirs()
        file.writeText(minted.value)
        return minted
    }
}
