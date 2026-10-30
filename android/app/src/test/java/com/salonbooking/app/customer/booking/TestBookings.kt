package com.salonbooking.app.customer.booking

import com.salonbooking.app.core.network.BookedServiceDto
import com.salonbooking.app.core.network.BookingDto
import com.salonbooking.app.core.network.BranchContactDto
import com.salonbooking.app.customer.mybookings.DeviceData
import com.salonbooking.app.customer.mybookings.DeviceStore
import kotlinx.coroutines.flow.MutableStateFlow

fun booking(reference: String, start: String, status: String = "CONFIRMED") = BookingDto(
    reference = reference,
    status = status,
    businessName = "Luna Hair Co",
    businessSlug = "luna-hair-co",
    accentColor = "#7A4E8C",
    branch = BranchContactDto("Fortitude Valley", "9 Brunswick Street", null, "Fortitude Valley", "QLD", "4006", "07 3000 1111", "Australia/Brisbane"),
    startAt = start,
    endAt = java.time.Instant.parse(start).plusSeconds(3600).toString(),
    services = listOf(BookedServiceDto("Cut & blow-dry", 60, 8500)),
    totalPriceCents = 8500,
    staffName = "Sam",
    customerName = "Riley Test",
    cancelBefore = java.time.Instant.parse(start).minusSeconds(86_400).toString(),
)

class InMemoryDeviceStore(initial: DeviceData = DeviceData()) : DeviceStore {
    override val data = MutableStateFlow(initial)
    override fun update(change: (DeviceData) -> DeviceData) {
        data.value = change(data.value)
    }
}
