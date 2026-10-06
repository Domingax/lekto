package app.lekto.core.llm

import app.lekto.testkit.FakeLlmClient
import app.lekto.testkit.LlmClientContract
import io.kotest.core.spec.style.FunSpec

/**
 * The shared [LlmClientContract] run against the scripted fake: the provider
 * adapter seam's specification, proved without a socket (issue #88; ADR-0022;
 * docs/testing.md, "Test levels"). The real transport runs its own request-shape
 * and status-mapping proofs against a local server in `jvmTest`.
 */
class LlmClientContractTest :
    FunSpec({

        LlmClientContract { FakeLlmClient() }.cases().forEach { case ->
            test(case.name) { case.body() }
        }
    })
