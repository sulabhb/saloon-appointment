package com.salonbooking.app.core.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SalonCodeTest {

    @Test
    fun `normalizes what customers type`() {
        assertEquals("glow-hair", SalonCode.normalize("  Glow-Hair "))
        assertEquals("abc", SalonCode.normalize("abc"))
    }

    @Test
    fun `rejects codes the backend would reject`() {
        listOf("", "ab", "-glow", "glow-", "glow hair", "glow_hair", "a".repeat(51)).forEach {
            assertNull("\"$it\" should be rejected", SalonCode.normalize(it))
        }
    }
}
