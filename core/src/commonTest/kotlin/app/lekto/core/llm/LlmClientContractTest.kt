package app.lekto.core.llm

import app.lekto.testkit.FakeLlmClient
import app.lekto.testkit.LlmClientContract
import io.kotest.core.spec.style.FunSpec

/**
 * The shared [LlmClientContract] run against the scripted fake: the provider
 * adapter seam's specification, proved without a socket (issues #88, #89;
 * ADR-0022; docs/testing.md, "Test levels"). Both halves — the blocking
 * connection test and the streaming translation — are driven here; the real
 * transport runs its own request-shape, status-mapping and server-sent-event
 * proofs against a local server in `jvmTest`.
 */
class LlmClientContractTest :
    FunSpec({

        val contract = LlmClientContract { FakeLlmClient() }

        contract.cases().forEach { case ->
            test(case.name) { case.body() }
        }

        contract.translationCases().forEach { case ->
            test(case.name) { case.body() }
        }
    })
