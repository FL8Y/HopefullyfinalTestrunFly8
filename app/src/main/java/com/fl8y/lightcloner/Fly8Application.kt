package com.fl8y.lightcloner

import android.app.Application
import android.content.Context
import com.fl8y.lightcloner.engine.Fly8VirtualRuntime

class Fly8Application : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        Fly8VirtualRuntime.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()
        Fly8VirtualRuntime.onApplicationCreate()
    }
}
