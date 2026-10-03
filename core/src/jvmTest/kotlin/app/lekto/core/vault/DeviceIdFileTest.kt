package app.lekto.core.vault

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files

/**
 * The installation's device id: persisted on first use, stable on later reads,
 * and re-read rather than re-minted (ADR-0003).
 */
class DeviceIdFileTest :
    FunSpec({

        test("the first read mints an id and writes it to the file") {
            val file = Files.createTempDirectory("lekto-device").resolve("device-id").toFile()

            val id = DeviceIdFile(file).get()

            file.isFile shouldBe true
            file.readText() shouldBe id.value
        }

        test("later reads return the same persisted id") {
            val file = Files.createTempDirectory("lekto-device").resolve("device-id").toFile()
            val first = DeviceIdFile(file).get()

            val second = DeviceIdFile(file).get()

            second shouldBe first
        }

        test("a pre-existing id is read rather than overwritten") {
            val file = Files.createTempFile("lekto-device", ".id").toFile()
            file.writeText("device-kept")

            DeviceIdFile(file).get() shouldBe DeviceId("device-kept")
        }

        test("the file is created even when its parent directory does not exist") {
            val file = File(Files.createTempDirectory("lekto-device").toFile(), "nested/dir/device-id")

            DeviceIdFile(file).get().value.isNotBlank() shouldBe true
            file.isFile shouldBe true
        }
    })
