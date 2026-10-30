package com.salonbooking.app.core.network

import com.salonbooking.app.BuildConfig
import com.salonbooking.app.core.update.ClientVersionInterceptor
import com.salonbooking.app.staff.session.StaffAuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun json(): Json = Json {
        // New response fields must not break older app versions (NFR-OPS-5).
        ignoreUnknownKeys = true
    }

    @Provides
    @Singleton
    fun okHttpClient(clientVersion: ClientVersionInterceptor, staffAuth: StaffAuthInterceptor): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(clientVersion)
        .addInterceptor(staffAuth)
        .apply { DebugNetworking.configure(this) }
        .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit = createRetrofit(BuildConfig.API_BASE_URL, client, json)

    @Provides
    @Singleton
    fun publicApi(retrofit: Retrofit): PublicApi = retrofit.create(PublicApi::class.java)

    @Provides
    @Singleton
    fun staffApi(retrofit: Retrofit): StaffApi = retrofit.create(StaffApi::class.java)

    fun createRetrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
