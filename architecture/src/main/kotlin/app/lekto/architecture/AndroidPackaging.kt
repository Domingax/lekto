package app.lekto.architecture

/**
 * The Android release packaging contract (issue #262) as a value.
 *
 * The APK is the one artefact a release publishes that no other lane compiles: a
 * signing configuration, a signature check and an asset name live only in the
 * workflow, so a dropped step is invisible until a tag ships a file nobody can
 * install. It names the assets a release promises it will carry; the workflow
 * file itself is read by [WorkflowJobs], the way [DesktopPackaging] reads the
 * installer matrix.
 */
object AndroidPackaging {

    /** The one asset name a release publishes, so the guide and the release body can name it. */
    const val APK_ASSET: String = "Lekto-${'$'}{version}-android.apk"

    /** The artifact the Android job uploads and the release job downloads. */
    const val ARTIFACT: String = "android"

    /** The Gradle task that builds the APK a release ships. */
    const val RELEASE_TASK: String = ":app:assembleRelease"
}
