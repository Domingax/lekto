# Architecture tests are a test-only module with hand-rolled rules

The module boundaries and naming conventions are asserted by a **hand-rolled rule
engine in a test-only `architecture` module**, and the assertions run inside
`./gradlew check` (ticket #9). The engine reads `settings.gradle.kts`, every
module's `build.gradle.kts` and every Kotlin source file into a small, pure model
— the modules and their declared project dependencies (tagged main or test), and
each source's package and imports — and the rules are functions over that model.
A violation is a failing test, so a boundary the compiler cannot see fails the
build instead of waiting for a review.

The boundary is the **Gradle module**, not the package: `core`, `testkit`,
`integrations/webdav`, `app`, `tools/dictionaries` (ADR-0007, and the spec's D3).
The rules assert the arrows between them — the domain depends on no module, only
the application may depend on an integration, nothing depends on the application
— plus import purity (the domain names neither the application, an integration,
nor Android; the testkit never reaches production) and naming and placement (a
package matches its directory, a source sits under its module's package root, a
test class is `*Test.kt` in a test source set).

Rejected alternatives:

- **Konsist.** The mature Kotlin architecture-testing library, but it is
  package-oriented, where the boundary here is the Gradle module, and it would be
  the project's first architecture-testing dependency. Hand-rolling keeps the
  suite dependency-free and able to read the module graph (ADR-0011 is satisfied
  either way, but a needless dependency is still a reason to decline one).
- **ArchUnit.** Works on compiled bytecode, so it can see package cycles but not
  the Gradle module graph, source placement, or a file's name.
- **A Gradle verification task in the root build**, like
  `checkDependencyLicences`. It would share the build logic with the toolchain it
  polices and its rules would not be testable as values; the ticket asks for
  tests, and a test can be fed a deliberately broken repository.

A **new module** is the home because no existing one fits: the rules must know the
whole graph, so they cannot live in `core` (the domain must not depend on the
application); `testkit` holds contract suites and fakes, not repository-wide
checks; and `app` is the thing under test. `architecture` reads the tree instead
of depending on the modules it polices, so it is a leaf and never joins the graph
it asserts.

Consequences we accept:

- **The rules parse build scripts and source text**, which is approximate where
  Gradle's own model would be exact. The approximation is confined to
  `GradleBuildFile` and the scanner, and each parser is tested directly; the
  scope distinction (a test source set may reach for `testkit`, production may
  not) is read from the brace context the build files actually use.
- **The module is test-only**, never published, not aggregated into coverage and
  not scanned by SonarCloud — the same posture as `testkit`. It is not shipped,
  so it is not a product surface.
- **A boundary can only change by editing `LektoArchitecture.policies`**, which
  is the point: adding a module or a dependency is a deliberate, reviewable edit,
  and a policy for a module that no longer exists fails `check`.
- **Each rule is proven against a broken repository.** `ArchitectureViolationTest`
  feeds every rule a synthetic violation and asserts it is caught, so removing or
  weakening a rule turns the suite red rather than silently relaxing the
  architecture.
