package com.salonbooking.app.staff.session

import com.salonbooking.app.core.network.ApiResult
import com.salonbooking.app.core.network.LoginDto
import com.salonbooking.app.core.network.MeDto
import com.salonbooking.app.core.network.SelectBusinessDto
import com.salonbooking.app.core.network.StaffApi
import com.salonbooking.app.core.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/** Holds the session token for the network layer; kept apart from [StaffSession] to avoid a dependency cycle. */
@Singleton
class SessionToken @Inject constructor(private val vault: TokenVault) {

    @Volatile
    var value: String? = runCatching { vault.read() }.getOrNull()
        private set

    /** Set when the server rejects the token (expired, revoked or access removed). */
    val rejected = MutableStateFlow(false)

    fun set(token: String) {
        vault.write(token)
        value = token
        rejected.value = false
    }

    fun clear() {
        vault.clear()
        value = null
    }
}

sealed interface SessionState {
    /** Checking a saved token with the server. */
    data object Restoring : SessionState
    data object SignedOut : SessionState

    /** Signed in; [me] says which business is selected, if any. */
    data class SignedIn(val me: MeDto) : SessionState
}

/** Staff sign-in, business choice and sign-out (APP-20, APP-28). */
@Singleton
class StaffSession @Inject constructor(
    private val api: StaffApi,
    private val token: SessionToken,
    private val json: Json,
) {

    private val _state = MutableStateFlow<SessionState>(if (token.value == null) SessionState.SignedOut else SessionState.Restoring)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Confirms a saved token at launch; an offline check keeps the restoring state for a retry. */
    suspend fun restore(): ApiResult<MeDto>? {
        if (token.value == null) {
            _state.value = SessionState.SignedOut
            return null
        }
        val result = apiCall(json) { api.me() }
        when (result) {
            is ApiResult.Ok -> _state.value = SessionState.SignedIn(result.value)
            is ApiResult.Failed -> if (result.status == 401) signedOutLocally()
            ApiResult.Offline -> Unit
        }
        return result
    }

    suspend fun signIn(email: String, password: String, deviceName: String): ApiResult<MeDto> {
        val result = apiCall(json) { api.login(LoginDto(email.trim(), password, client = "android", deviceName = deviceName)) }
        return when (result) {
            is ApiResult.Ok -> {
                token.set(result.value.token)
                _state.value = SessionState.SignedIn(result.value.me)
                ApiResult.Ok(result.value.me)
            }
            is ApiResult.Failed -> result
            ApiResult.Offline -> ApiResult.Offline
        }
    }

    suspend fun selectBusiness(businessId: String): ApiResult<MeDto> {
        val result = apiCall(json) { api.selectBusiness(SelectBusinessDto(businessId)) }
        if (result is ApiResult.Ok) _state.value = SessionState.SignedIn(result.value)
        return result
    }

    /** Ends the session on the server when possible; this device forgets it either way. */
    suspend fun signOut() {
        apiCall(json) { api.logout() }
        signedOutLocally()
    }

    /** The server no longer accepts the token: forget it and everything cached with it (APP-28). */
    fun signedOutLocally() {
        token.clear()
        _state.value = SessionState.SignedOut
    }
}
