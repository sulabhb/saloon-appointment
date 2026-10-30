package com.salonbooking.app.customer.salon

import com.salonbooking.app.core.network.BusinessDto
import com.salonbooking.app.core.network.PublicApi
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

sealed interface SalonResult {
    data class Found(val salon: BusinessDto) : SalonResult
    data object NotFound : SalonResult
    data object Unavailable : SalonResult
    data object NetworkError : SalonResult
}

interface SalonRepository {
    suspend fun loadSalon(code: String): SalonResult
}

@Singleton
class RemoteSalonRepository @Inject constructor(private val api: PublicApi) : SalonRepository {

    override suspend fun loadSalon(code: String): SalonResult = try {
        SalonResult.Found(api.business(code))
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        when (e.code()) {
            404 -> SalonResult.NotFound
            503 -> SalonResult.Unavailable
            else -> SalonResult.NetworkError
        }
    } catch (e: IOException) {
        SalonResult.NetworkError
    }
}
