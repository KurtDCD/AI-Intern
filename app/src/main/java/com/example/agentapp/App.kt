// App.kt
package com.example.agentapp

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize global resources, for example the notification channel.
        NotificationHelper.createNotificationChannel(this)
    }
}
