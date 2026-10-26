package app.lekto

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The launcher icon the manifest declares, and the files it names (issue #177).
 *
 * The mark's geometry is pinned by `AppIconTest`'s golden; this pins the wiring
 * around it, which no golden can see: a renamed drawable, a dropped monochrome
 * layer or a manifest that lost `android:icon` all ship a working app whose icon
 * is silent about the identity. It reads the tree rather than the built APK, so
 * it runs on the JVM lane with no Android SDK.
 */
class LauncherIconTest {

    @Test
    fun theManifestDeclaresTheLauncherIcon() {
        val application = element(document("AndroidManifest.xml"), "application")

        assertEquals("@mipmap/ic_launcher", application.getAttribute("android:icon"))
    }

    @Test
    fun theIconHasABackgroundAForegroundAndAMonochromeLayer() {
        val icon = document("res/mipmap-anydpi-v26/ic_launcher.xml")

        assertEquals("@color/lekto_paper_light", drawable(icon, "background"))
        assertEquals("@drawable/ic_launcher_foreground", drawable(icon, "foreground"))
        assertEquals("@drawable/ic_launcher_monochrome", drawable(icon, "monochrome"))
    }

    @Test
    fun everyLayerTheIconNamesExists() {
        val icon = document("res/mipmap-anydpi-v26/ic_launcher.xml")

        val foreground = drawable(icon, "foreground").substringAfter('/')
        val monochrome = drawable(icon, "monochrome").substringAfter('/')
        assertTrue(androidMain("res/drawable/$foreground.xml").isFile, "missing $foreground")
        assertTrue(androidMain("res/drawable/$monochrome.xml").isFile, "missing $monochrome")

        val paper = drawable(icon, "background").substringAfter('/')
        assertTrue(
            androidMain("res/values/colors.xml").readText().contains("name=\"$paper\""),
            "res/values/colors.xml does not declare $paper",
        )
    }

    @Test
    fun theLaunchScreenDrawsTheSameMarkAsTheLauncher() {
        // Android 12's splash attributes live in values-v31, one style per paper,
        // and the mark is the launcher's own layer: the system masks the same
        // outer third, so the launch screen and the icon are the same drawing.
        val v31 = androidMain("res/values-v31/themes.xml").readText()

        assertEquals(
            THEMES,
            Regex("@drawable/ic_launcher_foreground").findAll(v31).count(),
            "values-v31 must name the launcher's mark for each paper",
        )
        assertEquals(
            THEMES,
            Regex("@color/lekto_paper").findAll(v31).count(),
            "each style must paint its paper",
        )
    }
}

/** System, Light, Dark and Sepia: one launch style each. */
private const val THEMES: Int = 4

/** A file of the Android source set, resolved from wherever the test task starts. */
private fun androidMain(relative: String): File {
    var directory: File? = File(".").absoluteFile
    while (directory != null && !File(directory, "src/androidMain/AndroidManifest.xml").isFile) {
        directory = directory.parentFile
    }
    val module = checkNotNull(directory) { "the app module is not above ${File(".").absolutePath}" }
    return File(module, "src/androidMain/$relative")
}

private fun document(relative: String): Document {
    val factory = DocumentBuilderFactory.newInstance()
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    return factory.newDocumentBuilder().parse(androidMain(relative))
}

private fun element(document: Document, tag: String): Element {
    val nodes = document.getElementsByTagName(tag)
    check(nodes.length > 0) { "no <$tag> in the document" }
    return nodes.item(0) as Element
}

private fun drawable(document: Document, layer: String): String =
    element(document, layer).getAttribute("android:drawable")
