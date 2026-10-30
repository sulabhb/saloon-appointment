package com.salonbooking.app.core.link

import java.net.URI

/** What a link on the booking host opens in the app (APP-2). */
sealed interface AppLink {
    /** `/{salon}`, `/{salon}/{branch}`, or the web paths `/book/{salon}[/{branch}]`. */
    data class Salon(val code: String, val branch: String? = null) : AppLink

    /** `/m/{token}`: a booking's private management link from the confirmation email. */
    data class Booking(val token: String) : AppLink

    companion object {
        private val TOKEN = Regex("^[A-Za-z0-9_-]{20,100}$")

        fun parse(link: String, bookingHost: String): AppLink? {
            val uri = runCatching { URI(link) }.getOrNull() ?: return null
            if (uri.scheme != "https" || !uri.host.equals(bookingHost, ignoreCase = true)) return null
            val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
            if (segments.size == 2 && segments[0] == "m") {
                return segments[1].takeIf { TOKEN.matches(it) }?.let(::Booking)
            }
            val salonPath = if (segments.firstOrNull() == "book") segments.drop(1) else segments
            val code = salonPath.firstOrNull()?.let(SalonCode::normalize) ?: return null
            return Salon(code, salonPath.getOrNull(1)?.let(SalonCode::normalize))
        }
    }
}
