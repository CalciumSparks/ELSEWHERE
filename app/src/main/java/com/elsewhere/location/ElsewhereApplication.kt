package com.elsewhere.location

import android.app.Application
import org.osmdroid.config.Configuration

class ElsewhereApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Configuration.getInstance().apply {
            userAgentValue = "Elsewhere-Android/${BuildConfig.VERSION_NAME} (local virtual-location utility)"
            load(this@ElsewhereApplication, getSharedPreferences("osmdroid", MODE_PRIVATE))
        }
    }
}
