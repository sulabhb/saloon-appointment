package com.salonbooking.app.customer.booking

import com.salonbooking.app.customer.mybookings.DeviceStore
import com.salonbooking.app.customer.mybookings.PrefsDeviceStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BookingModule {

    @Binds
    abstract fun bookingRepository(remote: RemoteBookingRepository): BookingRepository

    @Binds
    abstract fun deviceStore(prefs: PrefsDeviceStore): DeviceStore
}
