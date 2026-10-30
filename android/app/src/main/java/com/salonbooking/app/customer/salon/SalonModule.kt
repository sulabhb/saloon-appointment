package com.salonbooking.app.customer.salon

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SalonModule {

    @Binds
    abstract fun salonRepository(impl: RemoteSalonRepository): SalonRepository
}
