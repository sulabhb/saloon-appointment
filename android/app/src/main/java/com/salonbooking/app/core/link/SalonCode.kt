package com.salonbooking.app.core.link

/**
 * Salon codes are business slugs (TEN-6). The same rules as the backend's business.slug check.
 */
object SalonCode {

    private val VALID = Regex("^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$")

    /** Trims and lower-cases what the customer typed; returns null if it cannot be a salon code. */
    fun normalize(input: String): String? = input.trim().lowercase().takeIf { VALID.matches(it) }
}
