package com.salonbooking.app.staff.session

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class StaffModule {

    @Binds
    abstract fun tokenVault(keystore: KeystoreTokenVault): TokenVault
}
