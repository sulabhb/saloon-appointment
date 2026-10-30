package com.salonbooking.app.staff.session

import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the staff session token to staff and auth requests (never to public booking calls), and
 * flags a 401 so the app can return to sign-in.
 */
class StaffAuthInterceptor @Inject constructor(private val token: SessionToken) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val staffCall = path.startsWith("/api/v1/staff/") || (path.startsWith("/api/v1/auth/") && !path.endsWith("/login"))
        val current = token.value
        val response = chain.proceed(
            if (staffCall && current != null) request.newBuilder().header("Authorization", "Bearer $current").build() else request,
        )
        if (staffCall && response.code == 401 && current != null) token.rejected.value = true
        return response
    }
}
