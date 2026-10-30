package com.salonbooking.app.core.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLinkTest {

    private val host = "book.example.com"
    private val token = "1Va3WjoiO2A2Be4wtCik4ieRVknRLDuYlZJ8WHmLOAI"

    @Test
    fun `opens a salon or a branch from booking links`() {
        assertEquals(AppLink.Salon("glow-hair"), AppLink.parse("https://book.example.com/glow-hair", host))
        assertEquals(AppLink.Salon("glow-hair", "sydney-cbd"), AppLink.parse("https://BOOK.example.com/glow-hair/sydney-cbd?x=1", host))
        // The web booking site's paths work too.
        assertEquals(AppLink.Salon("glow-hair", "sydney-cbd"), AppLink.parse("https://book.example.com/book/glow-hair/sydney-cbd", host))
    }

    @Test
    fun `opens a booking from its management link`() {
        assertEquals(AppLink.Booking(token), AppLink.parse("https://book.example.com/m/$token", host))
        assertNull(AppLink.parse("https://book.example.com/m/short", host))
        assertNull(AppLink.parse("https://book.example.com/m", host))
    }

    @Test
    fun `ignores links that are not booking links`() {
        assertNull(AppLink.parse("https://evil.example.com/glow-hair", host))
        assertNull(AppLink.parse("http://book.example.com/glow-hair", host))
        assertNull(AppLink.parse("https://book.example.com/", host))
        assertNull(AppLink.parse("not a link", host))
    }
}
