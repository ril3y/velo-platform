package io.freewheel.launcher

import android.app.Application
import io.freewheel.launcher.bridge.BridgeConnectionManager

class VeloLauncherApp : Application() {

    lateinit var bridgeConnectionManager: BridgeConnectionManager
        private set

    override fun onCreate() {
        super.onCreate()
        bridgeConnectionManager = BridgeConnectionManager(this)
        bridgeConnectionManager.connect()
    }

    companion object {
        fun get(context: android.content.Context): VeloLauncherApp {
            return context.applicationContext as VeloLauncherApp
        }
    }
}
