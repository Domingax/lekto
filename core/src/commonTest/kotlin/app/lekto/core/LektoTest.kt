package app.lekto.core

import kotlin.test.Test
import kotlin.test.assertEquals

class LektoTest {
    @Test
    fun greets() {
        assertEquals("Hello from Lekto", Lekto.greeting())
    }
}
