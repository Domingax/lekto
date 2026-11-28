# Dependencies

## Licences

Source is **AGPL-3.0** (ADR-0011). Every dependency on a production classpath
must be AGPL-3.0-compatible. Test-scope dependencies may carry a licence that is
incompatible but never conveyed with the app; those exceptions are listed in
`config/dependency-licences.txt`.

`./gradlew checkDependencyLicences` reads every runtime classpath and fails on a
licence that is not allowed. The policy is data, in
`config/dependency-licences.txt`; the verdict per dependency is written to
`build/reports/dependency-licences.txt`. The task is separate from `check`
because it resolves metadata at execution time, so CI runs it without the
configuration cache.

The dictionary pack is a separate **CC BY-SA 4.0** artifact and is never
committed here. The shipped **Sample catalogue** carries a small attributed
excerpt of it, not the pack (ADR-0049).

## Vulnerability alerts

Vulnerability alerts are enabled once per repository with
`gh api --method PUT repos/<owner>/<repo>/vulnerability-alerts`.
`.github/dependabot.yml` turns a dependency bump into a pull request, weekly and
grouped by ecosystem.

A Dependabot alert is acted on when the vulnerable code sits on a classpath the
application ships. An alert on a build or test classpath is dismissed with the
reason `tolerable_risk`, citing the classpath evidence. The evidence comes from
`./gradlew checkDependencyLicences` (each component is marked production or test)
and `./gradlew buildEnvironment` (the build classpath). A new alert arrives open
and is triaged again; a dismissal is not a mute button.

Every manifest the repository ships has an entry in `.github/dependabot.yml`;
`DependabotWatchRulesTest` fails `check` when one is missing.
