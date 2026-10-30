package com.salonbooking.app.staff.signin

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salonbooking.app.core.network.ApiResult
import com.salonbooking.app.customer.booking.BookingFlowViewModel
import com.salonbooking.app.staff.session.SessionState
import com.salonbooking.app.staff.session.SessionToken
import com.salonbooking.app.staff.session.StaffSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignInForm(
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

/** Decides what staff mode shows: sign-in, choosing a business, or the day (APP-20). */
@HiltViewModel
class StaffGateViewModel @Inject constructor(
    private val session: StaffSession,
    token: SessionToken,
) : ViewModel() {

    val state: StateFlow<SessionState> = session.state

    private val _form = MutableStateFlow(SignInForm())
    val form: StateFlow<SignInForm> = _form.asStateFlow()

    private val _restoreError = MutableStateFlow<String?>(null)
    val restoreError: StateFlow<String?> = _restoreError.asStateFlow()

    init {
        restore()
        // Access removed, session revoked or expired: back to sign-in, with nothing kept (STF-9, APP-28).
        viewModelScope.launch {
            token.rejected.collect { rejected -> if (rejected) session.signedOutLocally() }
        }
    }

    fun restore() {
        _restoreError.value = null
        viewModelScope.launch {
            if (session.restore() == ApiResult.Offline) _restoreError.value = BookingFlowViewModel.OFFLINE
        }
    }

    fun edit(email: String? = null, password: String? = null) =
        _form.update { it.copy(email = email ?: it.email, password = password ?: it.password, error = null) }

    fun signIn() {
        val f = _form.value
        if (f.busy || f.email.isBlank() || f.password.isEmpty()) return
        _form.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = session.signIn(f.email, f.password, Build.MODEL ?: "Android")
            _form.update {
                when (result) {
                    is ApiResult.Ok -> SignInForm(email = f.email)
                    is ApiResult.Failed -> it.copy(busy = false, password = "", error = result.message)
                    ApiResult.Offline -> it.copy(busy = false, error = BookingFlowViewModel.OFFLINE)
                }
            }
        }
    }

    fun selectBusiness(businessId: String) {
        viewModelScope.launch {
            val result = session.selectBusiness(businessId)
            if (result is ApiResult.Failed) _restoreError.value = result.message
            if (result == ApiResult.Offline) _restoreError.value = BookingFlowViewModel.OFFLINE
        }
    }

    fun signOut() = viewModelScope.launch { session.signOut() }
}
