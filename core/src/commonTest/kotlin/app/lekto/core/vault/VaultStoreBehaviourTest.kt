package app.lekto.core.vault

import app.lekto.testkit.FlakyVaultFileSystem
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.SimulatedWriteFailure
import app.lekto.testkit.testVaultRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.test.assertFailsWith

/**
 * The behaviours the shared contract does not reach: a lost manifest, a failed
 * manifest write, a record that changes kind, a path-escaping id, and derived
 * assets staying out of the vault and its export (ticket #12; ADR-0005).
 */
class VaultStoreBehaviourTest :
    FunSpec({

        test("reads every record even when the manifest file is missing") {
            val files = InMemoryVaultFileSystem()
            val store = JsonVaultStore(files)
            store.put(testVaultRecord("a"))
            store.put(testVaultRecord("b", kind = "progress"))
            files.delete(VaultFormat.MANIFEST_FILE)

            val reopened = JsonVaultStore(files)

            reopened.all().map { it.id }.toSet() shouldBe setOf("a", "b")
            reopened.get("a") shouldBe testVaultRecord("a")
        }

        test("a failed manifest write does not hide the record already written") {
            val files = FlakyVaultFileSystem()
            val store = JsonVaultStore(files)
            store.put(testVaultRecord("a"))

            files.failOn = { it == VaultFormat.MANIFEST_FILE }
            val updated = testVaultRecord("a", updatedAtMillis = 2)
            assertFailsWith<SimulatedWriteFailure> { store.put(updated) }

            store.get("a") shouldBe updated
            store.all().single() shouldBe updated
        }

        test("put moves a record when its kind changes rather than orphaning it") {
            val files = InMemoryVaultFileSystem()
            val store = JsonVaultStore(files)
            store.put(testVaultRecord("a", kind = "vocabulary"))
            store.put(testVaultRecord("a", kind = "progress"))

            files.listFiles().sorted() shouldBe listOf("manifest.json", "progress/a.json")
            store.all().single().kind shouldBe "progress"
        }

        test("put refuses a record whose id would escape the vault root") {
            val store = JsonVaultStore(InMemoryVaultFileSystem())

            assertFailsWith<IllegalArgumentException> { store.put(testVaultRecord("../escape")) }
        }

        test("a record is one JSON file under its kind, beside the manifest") {
            val files = InMemoryVaultFileSystem()
            JsonVaultStore(files).put(testVaultRecord("a"))

            files.listFiles().sorted() shouldBe listOf("manifest.json", "vocabulary/a.json")
        }

        test("derived assets are absent from the vault and from its export") {
            val vaultFiles = InMemoryVaultFileSystem()
            val vault = JsonVaultStore(vaultFiles)
            vault.put(testVaultRecord("a", text = "user-authored"))

            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            derived.put("parsed/book.txt", "parsed book text".encodeToByteArray())

            vaultFiles.listFiles().none { it.startsWith("parsed/") } shouldBe true
            vault.exportBundle().decodeToString().contains("parsed book text") shouldBe false
            vault.exportBundle().decodeToString().contains("user-authored") shouldBe true
        }

        test("derived assets round-trip and clear independently of the vault") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())
            derived.put("parsed/book.txt", "text".encodeToByteArray())

            derived.get("parsed/book.txt")!!.decodeToString() shouldBe "text"
            derived.paths() shouldBe listOf("parsed/book.txt")

            derived.remove("parsed/book.txt")
            derived.get("parsed/book.txt") shouldBe null

            derived.put("pack/fr.dict", byteArrayOf(1))
            derived.clear()
            derived.paths() shouldBe emptyList()
        }

        test("derived assets refuse a path that escapes the store root") {
            val derived = DerivedAssetStore(InMemoryVaultFileSystem())

            assertFailsWith<IllegalArgumentException> { derived.put("../escape", byteArrayOf()) }
        }

        test("import refuses a bundle whose manifest and records disagree") {
            val bundle = VaultBundle(
                manifest = VaultManifest(records = listOf(RecordRef.of(testVaultRecord("b")))),
                records = listOf(testVaultRecord("a")),
            )

            assertFailsWith<VaultFormatException> {
                JsonVaultStore(InMemoryVaultFileSystem())
                    .importBundle(VaultCodec.encodeBundle(bundle).encodeToByteArray())
            }
        }

        test("import refuses a bundle carrying two records with the same id") {
            val first = testVaultRecord("a", kind = "vocabulary")
            val second = testVaultRecord("a", kind = "progress")
            val bundle = VaultBundle(
                manifest = VaultManifest(records = listOf(RecordRef.of(first), RecordRef.of(second))),
                records = listOf(first, second),
            )

            assertFailsWith<VaultFormatException> {
                JsonVaultStore(InMemoryVaultFileSystem())
                    .importBundle(VaultCodec.encodeBundle(bundle).encodeToByteArray())
            }
        }
    })
