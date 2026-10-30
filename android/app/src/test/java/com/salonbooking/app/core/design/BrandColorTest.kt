package com.salonbooking.app.core.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrandColorTest {

    @Test
    fun `parses hex brand colours`() {
        assertEquals(0x5E7A68, BrandColor.parse("#5E7A68"))
        assertEquals(0x9c5b6b, BrandColor.parse(" #9c5b6b "))
    }

    @Test
    fun `ignores invalid colours`() {
        listOf(null, "", "5E7A68", "#5E7A6", "#5E7A68FF", "red").forEach { assertNull(BrandColor.parse(it)) }
    }

    @Test
    fun `contrast ratio matches WCAG reference values`() {
        assertEquals(21.0, BrandColor.contrastRatio(0x000000, 0xFFFFFF), 0.01)
        assertEquals(1.0, BrandColor.contrastRatio(0x777777, 0x777777), 0.01)
    }

    @Test
    fun `uses white text on dark accents and dark text on light accents`() {
        assertEquals(0xFFFFFF, BrandColor.readableTextOn(0x2B4A3A))
        assertEquals(0x1A1A1A, BrandColor.readableTextOn(0xF4D35E))
    }
}
