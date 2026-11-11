package com.promocodeapp.ui

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.promocodeapp.location.Permissions
import com.promocodeapp.notifications.DeepLinks
import com.promocodeapp.ui.coupons.CouponDetailScreen
import com.promocodeapp.ui.coupons.CouponEditScreen
import com.promocodeapp.ui.coupons.CouponListScreen
import com.promocodeapp.ui.memberships.MembershipEditScreen
import com.promocodeapp.ui.memberships.MembershipListScreen
import com.promocodeapp.ui.places.PlaceEditScreen
import com.promocodeapp.ui.places.PlaceListScreen
import com.promocodeapp.ui.settings.AccountScreen
import com.promocodeapp.ui.settings.SettingsScreen

object Routes {
    const val COUPONS = "coupons?placeId={placeId}&filter={filter}"
    const val COUPON = "coupon/{id}"
    const val COUPON_EDIT = "coupon_edit?id={id}"
    const val MEMBERSHIPS = "memberships"
    const val MEMBERSHIP_EDIT = "membership_edit?id={id}"
    const val PLACES = "places"
    const val PLACE_EDIT = "place_edit?id={id}&name={name}"
    const val SETTINGS = "settings"
    const val ACCOUNT = "account"

    fun coupon(id: String) = "coupon/$id"
    fun couponEdit(id: String? = null) = if (id == null) "coupon_edit" else "coupon_edit?id=$id"
    fun membershipEdit(id: String? = null) = if (id == null) "membership_edit" else "membership_edit?id=$id"
    fun placeEdit(id: String? = null, name: String = "") = when {
        id != null -> "place_edit?id=$id"
        name.isNotBlank() -> "place_edit?name=${Uri.encode(name)}"
        else -> "place_edit"
    }
}

private const val NEW_PLACE_KEY = "new_place_id"

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.COUPONS, "Coupons", Icons.Default.LocalOffer),
    Tab(Routes.MEMBERSHIPS, "Memberships", Icons.Default.CardMembership),
    Tab(Routes.PLACES, "Places", Icons.Default.Place),
    Tab(Routes.SETTINGS, "Settings", Icons.Default.Settings),
)

private val optionalString = { name: String ->
    navArgument(name) { type = NavType.StringType; nullable = true; defaultValue = null }
}

@Composable
fun PromoCodeNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val context = LocalContext.current
    var alertsNeedSetup by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        alertsNeedSetup = !Permissions.canGeofence(context) || !Permissions.canNotify(context)
    }

    fun switchTab(route: String) {
        // The coupons tab carries optional arguments; navigate to its bare form.
        navController.navigate(route.substringBefore('?')) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == currentRoute }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { switchTab(tab.route) },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.COUPONS,
            // Screens draw their own top bars; only the bottom bar is reserved here.
            modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())
                .consumeWindowInsets(androidx.compose.foundation.layout.PaddingValues(bottom = padding.calculateBottomPadding())),
        ) {
            composable(
                Routes.COUPONS,
                arguments = listOf(optionalString("placeId"), optionalString("filter")),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "${DeepLinks.SCHEME}://place/{placeId}" },
                    navDeepLink { uriPattern = "${DeepLinks.SCHEME}://coupons?filter={filter}" },
                ),
            ) {
                CouponListScreen(
                    onOpenCoupon = { navController.navigate(Routes.coupon(it)) },
                    onAddCoupon = { navController.navigate(Routes.couponEdit()) },
                    onSetUpAlerts = { switchTab(Routes.SETTINGS) },
                    alertsNeedSetup = alertsNeedSetup,
                )
            }
            composable(
                Routes.COUPON,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink { uriPattern = "${DeepLinks.SCHEME}://coupon/{id}" }),
            ) {
                CouponDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.couponEdit(it)) },
                )
            }
            composable(Routes.COUPON_EDIT, arguments = listOf(optionalString("id"))) { entry ->
                val newPlaceId by entry.savedStateHandle.getStateFlow<String?>(NEW_PLACE_KEY, null).collectAsStateWithLifecycle()
                CouponEditScreen(
                    onDone = { navController.popBackStack() },
                    onCreatePlace = { navController.navigate(Routes.placeEdit(name = it)) },
                    newPlaceId = newPlaceId,
                    onNewPlaceConsumed = { entry.savedStateHandle[NEW_PLACE_KEY] = null },
                )
            }
            composable(Routes.MEMBERSHIPS) {
                MembershipListScreen(
                    onOpen = { navController.navigate(Routes.membershipEdit(it)) },
                    onAdd = { navController.navigate(Routes.membershipEdit()) },
                )
            }
            composable(
                Routes.MEMBERSHIP_EDIT,
                arguments = listOf(optionalString("id")),
                deepLinks = listOf(navDeepLink { uriPattern = "${DeepLinks.SCHEME}://membership/{id}" }),
            ) { entry ->
                val newPlaceId by entry.savedStateHandle.getStateFlow<String?>(NEW_PLACE_KEY, null).collectAsStateWithLifecycle()
                MembershipEditScreen(
                    onDone = { navController.popBackStack() },
                    onCreatePlace = { navController.navigate(Routes.placeEdit(name = it)) },
                    newPlaceId = newPlaceId,
                    onNewPlaceConsumed = { entry.savedStateHandle[NEW_PLACE_KEY] = null },
                )
            }
            composable(Routes.PLACES) {
                PlaceListScreen(
                    onOpenPlace = { navController.navigate(Routes.placeEdit(it)) },
                    onAddPlace = { navController.navigate(Routes.placeEdit()) },
                )
            }
            composable(Routes.PLACE_EDIT, arguments = listOf(optionalString("id"), optionalString("name"))) {
                PlaceEditScreen(
                    onCancel = { navController.popBackStack() },
                    onSaved = { placeId ->
                        // Hand the new place back to the form that asked for it.
                        navController.previousBackStackEntry.takeIfEditor()?.savedStateHandle?.set(NEW_PLACE_KEY, placeId)
                        navController.popBackStack()
                    },
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen(onOpenAccount = { navController.navigate(Routes.ACCOUNT) }) }
            composable(Routes.ACCOUNT) { AccountScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

private fun NavBackStackEntry?.takeIfEditor(): NavBackStackEntry? =
    this?.takeIf { it.destination.route == Routes.COUPON_EDIT || it.destination.route == Routes.MEMBERSHIP_EDIT }
