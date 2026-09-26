package com.roziqrizal.rizqflow

import android.app.Application
import com.roziqrizal.rizqflow.notifications.ReminderNotifier

class RizqflowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderNotifier.ensureChannel(this)
    }
}
