package com.salonbooking.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.salonbooking.app.core.link.AppLink
import com.salonbooking.app.customer.booking.BookingFlowScreen
import com.salonbooking.app.customer.manage.ManageBookingScreen
import com.salonbooking.app.customer.salon.SalonScreen
import com.salonbooking.app.customer.salonentry.HomeScreen
import com.salonbooking.app.staff.StaffScreen
import com.salonbooking.app.staff.appointment.StaffAppointmentScreen
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class SalonRoute(val code: String)

/** Booking at one branch; ViewModels read these keys from saved state. */
@Serializable
data class BookRoute(val code: String, val branch: String)

/** One booking, opened through its private management token. */
@Serializable
data class ManageRoute(val token: String)

/** Staff mode: sign in, then the day. */
@Serializable
data object StaffRoute

/** One appointment from the staff side; the ViewModel reads [id] from saved state. */
@Serializable
data class StaffAppointmentRoute(val id: String)

/**
 * @param link a booking or management link that opened the app, if any.
 * @param onLinkHandled called once navigation to [link] has happened.
 */
@Composable
fun AppNavHost(link: AppLink?, onLinkHandled: () -> Unit) {
    val navController = rememberNavController()

    LaunchedEffect(link) {
        when (link) {
            // Replace whatever is open with the linked screen; launchSingleTop would keep an old
            // screen of the same type because it matches the destination, not its arguments.
            is AppLink.Salon -> navController.navigate(link.branch?.let { BookRoute(link.code, it) } ?: SalonRoute(link.code)) {
                popUpTo<HomeRoute>()
            }
            is AppLink.Booking -> navController.navigate(ManageRoute(link.token)) { popUpTo<HomeRoute>() }
            null -> return@LaunchedEffect
        }
        onLinkHandled()
    }

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenSalon = { code -> navController.navigate(SalonRoute(code)) },
                onOpenBooking = { token -> navController.navigate(ManageRoute(token)) },
                onStaff = { navController.navigate(StaffRoute) },
            )
        }
        composable<StaffRoute> {
            StaffScreen(
                onBack = { navController.popBackStack() },
                onOpenAppointment = { id -> navController.navigate(StaffAppointmentRoute(id)) },
            )
        }
        composable<StaffAppointmentRoute> {
            StaffAppointmentScreen(onBack = { navController.popBackStack() })
        }
        composable<SalonRoute> { entry ->
            val salon = entry.arguments?.getString("code").orEmpty()
            SalonScreen(
                onBack = { navController.popBackStack() },
                onBook = { branch -> navController.navigate(BookRoute(salon, branch)) },
            )
        }
        composable<BookRoute> {
            BookingFlowScreen(
                onClose = { navController.popBackStack(HomeRoute, inclusive = false) },
                onOpenBooking = { token -> navController.navigate(ManageRoute(token)) { popUpTo<HomeRoute>() } },
            )
        }
        composable<ManageRoute> {
            ManageBookingScreen(
                onBack = { if (!navController.popBackStack()) navController.navigate(HomeRoute) },
                onBookAgain = { salon -> navController.navigate(SalonRoute(salon)) { popUpTo<HomeRoute>() } },
            )
        }
    }
}
