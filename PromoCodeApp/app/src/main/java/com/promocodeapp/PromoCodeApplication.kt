package com.promocodeapp

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.crashlytics
import com.promocodeapp.service.PromoCodeMessagingService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PromoCodeApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        val analytics = Firebase.analytics
        val crashlytics = Firebase.crashlytics

        // Subscribe to notification topics
        PromoCodeMessagingService.subscribeToTopics()
    }
}
