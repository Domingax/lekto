# The test harness

The fast, deterministic loop every later ticket is written against. The domain is
pure Kotlin, so the whole suite runs on the JVM in seconds — no emulator, no
Docker. Non-determinism enters only through injected seams.

## The one command

```sh
./gradlew check
```

It builds every module and runs the domain suite (`core`) and the UI-semantics
suite (`app`). The narrow version for the inner loop is:

```sh
./gradlew :core:jvmTest :app:desktopTest
```

Force a re-run when Gradle marks the task up-to-date:

```sh
./gradlew :core:jvmTest --rerun
```

## What runs where

| Suite                | Source set        | Task                  | Needs        |
| -------------------- | ----------------- | --------------------- | ------------ |
| Domain + properties  | `core/commonTest` | `:core:jvmTest`       | a JVM        |
| UI semantics         | `app/desktopTest` | `:app:desktopTest`    | a JVM        |

The UI-semantics suite lives in `desktopTest`, not `commonTest`, because the
Compose Multiplatform common test API cannot run under Android's local (host)
test configuration. The slower lanes — screenshot goldens, a containerised
WebDAV driver and instrumented end-to-end runs — are added by later tickets and
never sit on this fast path.

## The framework

- **`kotlin.test`** — assertions everywhere, as the baseline. On the JVM it maps
  to JUnit 5.
- **Kotest** — the spec style (`FunSpec`), matchers (`shouldBe`), and property
  testing (`forAll`/`checkAll`). Kotest runs on the JUnit Platform; the `jvm`
  target configures `useJUnitPlatform()` and adds `kotest-runner-junit5`.
- **Turbine** — flow assertions (`flow.test { … }`), which fail instead of
  hanging and assert the flow is complete.
- **`kotlinx-coroutines-test`** — `runTest`, `TestScope` and virtual time for the
  async code that lands later.

All four are test-scope. Kotest, Turbine and `kotlinx-coroutines-test` are
Apache-2.0, which is AGPL-compatible (ADR-0011). Kotest's JUnit 5 runner and
`kotlin.test` on the JVM pull in the **JUnit Platform / Jupiter**, which are
EPL-2.0 and *not* GPL-compatible. They are safe here only because they are
test-scope: they are never linked into the shipped app and never conveyed with
it. Keep every JUnit type out of production code — Kotest and `kotlin.test`
cover the test classpath. See `docs/research/testing-harness.md` §2.

## Deterministic seams

Time, identifiers and randomness are the only ways non-determinism enters the
domain. Each is an injected parameter, never an ambient global:

| Seam           | Production        | Test (in `testkit`)       |
| -------------- | ----------------- | ------------------------- |
| Time           | `Clock.System`    | `TestClock`               |
| Identifiers    | `UuidIdGenerator` | `SequentialIdGenerator`   |
| Randomness     | `Random.Default`  | `Random(seed)`            |

`app.lekto.core.Seams` bundles the three so they can be threaded as one value;
`Seams.system()` is the production wiring and `deterministicSeams()` (testkit) is
the test wiring. The domain never reads a wall clock or a global RNG directly.

Dispatchers are injected the same way — as a `CoroutineDispatcher` parameter on
whatever asynchronous code needs one — but they are not part of `Seams` yet,
because the domain holds no asynchronous code. `kotlinx-coroutines-test`
supplies the virtual time (`runTest`, `TestScope`, `advanceUntilIdle`) for them
when the sync engine lands; a hard-coded `Dispatchers.IO` would not see it.

## Reproducing a failure

A property failure prints the seed it used and writes it to
`~/.kotest/seeds/<spec>/<testname>`. The next run replays that seed
automatically until the test passes; a seed passed to a `PropTestConfig` takes
precedence. `HarnessSelfTest` pins both guarantees: a broken assertion fails, and
a failing property reports a replayable seed.

A KMP build can silently compile zero tests, or run them under a runner that
never discovers them, and still report success. `HarnessSelfTest` exercises each
capability above so the wiring cannot regress unnoticed.
