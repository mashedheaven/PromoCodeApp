package com.promocodeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.promocodeapp.presentation.ui.CouponListScreen
import com.promocodeapp.ui.theme.PromoCodeAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PromoCodeAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // TODO: Set up navigation graph
                    // For now, show the coupon list screen
                    CouponListScreen(
                        userId = "user_123", // TODO: Get from authentication
                        onNavigateToDetail = { couponId ->
                            // Handle navigation to coupon detail
                        },
                        onNavigateToCreateCoupon = {
                            // Handle navigation to create coupon
                        }
                    )
                }
            }
        }
    }
}
