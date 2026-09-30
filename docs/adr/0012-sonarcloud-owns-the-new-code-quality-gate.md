# Measurement is SonarCloud; the gate blocks on the change's new code

Coverage is measured with **Kover**, and the code-quality service is
**SonarCloud** (ADR-0012). SonarCloud ingests three reports the CI build writes —
Kover's JaCoCo-compatible coverage XML, the detekt and ktlint findings — and
evaluates a **quality gate on new code**: new-code coverage, duplicated lines and
code smells. The scan waits for the gate (`sonar.qualitygate.wait=true`) and exits
non-zero when it fails, so the `sonar` CI job is what blocks a pull request.

The point is to measure **the change under review**, not the project as a whole.
A project-wide percentage is a number nobody acts on; a gate on the added lines is
feedback the author can still answer. Kover is the KMP-native coverage engine and
the outcome JaCoCo cannot give for `commonMain` compiled to `jvmTest`. SonarCloud
is hosted, free for public repositories, and needs no server — consistent with a
product that has no backend (ADR-0002) — and it owns the duplication and smell
metrics, so the repository does not reimplement them.

Consequences we accept:

- **The scanner is a GitHub action, not a Gradle plugin.** Nothing SonarCloud owns
  becomes a build dependency; Kover is the only new one, and it is Apache-2.0
  (ADR-0011). The service reads `sonar-project.properties` for its report paths.
- **A fork's pull request cannot run the scan.** GitHub does not expose repository
  secrets to fork events, so the job produces the reports but skips the scan. The
  gate therefore guards the maintainer's own branches; a `workflow_run` rewrite
  could extend it to forks if that ever matters. This is the same shape as the
  WebDAV lane skipping without Docker.
- **Android platform glue is outside the coverage gate** until the nightly
  instrumented lane measures it: only the JVM/desktop suites run on the fast path,
  so coverage on `app/src/androidMain` would always read as zero. The exclusion is
  named in `sonar-project.properties` and removed when that lane lands.
- **The gate lives in the SonarCloud UI, not the repository.** The default
  "Sonar way" gate is the expected configuration; changing a threshold is a
  dashboard action, and ADR-0012 is the record of why the gate exists.
- **SonarCloud's Automatic Analysis must be off**, or it would scan without the
  coverage and linter reports the CI job supplies and could report against a
  different revision. The project is created with GitHub sign-in and the
  repository's `sonar.projectKey` / `sonar.organization`, then automatic analysis
  is disabled in the project settings.

Rejected: a self-hosted SonarQube server (a service to run and upgrade, and a
second thing a contributor must stand up); reimplementing a new-code gate in
Gradle from the Kover XML and the linter outputs (only coverage, and it would
drift from the service's duplication and smell rules); and coverage without a
gate (measurement that nothing enforces is decoration).
