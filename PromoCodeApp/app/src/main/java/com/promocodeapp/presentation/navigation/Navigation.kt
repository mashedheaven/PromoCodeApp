package com.promocodeapp.presentation.navigation

sealed class Screen(val route: String) {
    object CouponList : Screen("coupon_list")
    object CouponDetail : Screen("coupon_detail/{couponId}") {
        fun createRoute(couponId: Long) = "coupon_detail/$couponId"
    }
    object CreateCoupon : Screen("create_coupon")
    object EditCoupon : Screen("edit_coupon/{couponId}") {
        fun createRoute(couponId: Long) = "edit_coupon/$couponId"
    }
    object MembershipList : Screen("membership_list")
    object CreateMembership : Screen("create_membership")
    object Settings : Screen("settings")
    object Login : Screen("login")
    object SignUp : Screen("signup")
}
