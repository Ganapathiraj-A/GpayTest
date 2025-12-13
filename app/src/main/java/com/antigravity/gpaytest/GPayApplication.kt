package com.antigravity.gpaytest

import android.app.Application
import com.google.firebase.FirebaseApp

class GPayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Firebase is typically initialized automatically by the ContentProvider from the plugin,
        // but explicit initialization check doesn't hurt.
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
    }
}
