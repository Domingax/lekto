package app.lekto

/**
 * The version an installed copy reports on the **About** screen (issue #214).
 *
 * It mirrors the `versionName` `app/build.gradle.kts` gives the Android build, so
 * the number a reader sees and the number the store sees cannot drift apart:
 * `AppVersionSynchronisationTest` in `architecture` fails when the two disagree.
 */
const val APP_VERSION: String = "0.1.0"
