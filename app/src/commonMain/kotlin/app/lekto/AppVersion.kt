package app.lekto

/**
 * The version an installed copy reports on the **About** screen (issue #214).
 *
 * It mirrors `lekto.version` in `gradle.properties`, the single source the Android
 * `versionName` and the desktop installers' `packageVersion` also read, so the
 * number a reader sees and the number a store sees cannot drift apart:
 * `AppVersionSynchronisationTest` in `architecture` fails when the two disagree.
 */
const val APP_VERSION: String = "1.1.0"
