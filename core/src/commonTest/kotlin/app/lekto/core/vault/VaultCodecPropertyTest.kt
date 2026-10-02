package app.lekto.core.vault

import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.testVaultRecord
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

/** The safe path segments a generated vault's records are keyed by. */
private val SAFE_IDS = arrayOf("a", "b", "c", "vocabulary-1", "progress_2", "book.epub", "id0")

/**
 * The vault's serialisation round-trips, over generated data (ticket #12;
 * docs/testing.md, "Test levels"): a record survives encode → decode whatever
 * its text, and a whole vault survives export → import whatever its records.
 *
 * The second property is the portability guarantee stated as an invariant: an
 * import can never be observationally different from the export that produced
 * it.
 */
@OptIn(ExperimentalKotest::class)
class VaultCodecPropertyTest :
    FunSpec({

        test("a record survives a JSON round-trip, for arbitrary text") {
            forAll(
                PropTestConfig(seed = 20261002, iterations = 300),
                Arb.string(),
                Arb.string(),
                Arb.string(),
                Arb.long(-10_000_000_000_000L..10_000_000_000_000L),
                Arb.string(),
                Arb.int(),
            ) { id, kind, text, millis, device, schema ->
                val record = VaultRecord(
                    id = id,
                    kind = kind,
                    schemaVersion = schema,
                    updatedAt = Instant.fromEpochMilliseconds(millis),
                    deviceId = DeviceId(device.ifBlank { "device" }),
                    body = buildJsonObject { put("text", text) },
                )

                VaultCodec.decodeRecord(VaultCodec.encodeRecord(record)) == record
            }
        }

        test("export then import restores a vault exactly, for generated vaults") {
            forAll(
                PropTestConfig(seed = 20261003, iterations = 200),
                Arb.list(Arb.element(*SAFE_IDS), 1..8),
            ) { ids ->
                val records = LinkedHashMap<String, VaultRecord>()
                ids.forEachIndexed { index, id -> records[id] = testVaultRecord(id, updatedAtMillis = index.toLong()) }

                val source = InMemoryVaultStore()
                records.values.forEach(source::put)
                val restored = InMemoryVaultStore()
                restored.importBundle(source.exportBundle())

                restored.all().associateBy { it.id } == records
            }
        }
    })
