package app.lekto.core.sync

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The configuration's own rules (issue #28): a target is usable only with both a
 * server address and a username, sync starts off, and the default is what an
 * app that has never configured sync holds.
 */
class SyncSettingsTest :
    FunSpec({

        test("the default configuration is unset and disabled") {
            SyncSettings.DEFAULT.isConfigured shouldBe false
            SyncSettings.DEFAULT.enabled shouldBe false
        }

        test("a server address and a username make a configuration usable") {
            SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "reader").isConfigured shouldBe true
        }

        test("a server address without a username is not usable") {
            SyncSettings(serverUrl = "https://cloud.example.test/dav").isConfigured shouldBe false
        }

        test("a username without a server address is not usable") {
            SyncSettings(username = "reader").isConfigured shouldBe false
        }
    })
