package com.brian.grokbar

import android.app.Application
import com.brian.grokbar.work.RefreshScheduler

class GrokBarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RefreshScheduler.ensure(this)
    }
}
