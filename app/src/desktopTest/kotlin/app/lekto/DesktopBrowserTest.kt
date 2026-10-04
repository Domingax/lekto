package app.lekto

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The desktop reference-shortcut opener (issue #19): the panel hands it a URL and
 * it browses it through the desktop's default browser. The browse call is
 * injected, so the test does not open a real window; a malformed URL is swallowed
 * so the reading session is never interrupted.
 */
class DesktopBrowserTest {

    @Test
    fun opensTheUrlThroughTheBrowser() {
        var opened: URI? = null

        openInBrowser("https://context.reverso.net/translation/english-french/lantern") { uri -> opened = uri }

        assertEquals(URI("https://context.reverso.net/translation/english-french/lantern"), opened)
    }

    @Test
    fun aMalformedUrlIsSwallowedRatherThanThrown() {
        var opened: URI? = null

        openInBrowser("not a url") { uri -> opened = uri }

        assertNull(opened)
    }
}
