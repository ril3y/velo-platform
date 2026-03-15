package io.freewheel.launcher.session

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.util.Log
import io.freewheel.fit.IWorkoutListener
import io.freewheel.fit.IWorkoutSession
import io.freewheel.launcher.VeloLauncherApp
import io.freewheel.launcher.bridge.BridgeConnectionManager

/**
 * Bound service exposing IWorkoutSession AIDL to external workout/game apps.
 * Apps bind to action "io.freewheel.launcher.WORKOUT_SESSION" and use this
 * to start/stop workouts, set resistance, and receive sensor data.
 */
class WorkoutSessionService : Service() {

    companion object {
        private const val TAG = "WorkoutSessionSvc"
    }

    private lateinit var bridge: BridgeConnectionManager

    override fun onCreate() {
        super.onCreate()
        bridge = VeloLauncherApp.get(this).bridgeConnectionManager
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private val binder = object : IWorkoutSession.Stub() {

        override fun requestStart(callerPackage: String): Boolean {
            validateCaller(callerPackage)

            val currentOwner = bridge.getActiveOwnerPackage()
            if (currentOwner != null && currentOwner != callerPackage) {
                Log.w(TAG, "requestStart denied: $currentOwner already owns the session")
                return false
            }

            if (!bridge.connected.value) {
                Log.w(TAG, "requestStart denied: bridge not connected")
                return false
            }

            val ok = bridge.startWorkout()
            if (ok) {
                val label = getAppLabel(callerPackage)
                bridge.setActiveOwner(callerPackage, label, getCallerBinder())
                Log.i(TAG, "Workout started for $callerPackage ($label)")
            }
            return ok
        }

        override fun requestStop(callerPackage: String): Boolean {
            validateCaller(callerPackage)

            val currentOwner = bridge.getActiveOwnerPackage()
            if (currentOwner != null && currentOwner != callerPackage) {
                Log.w(TAG, "requestStop denied: $callerPackage is not the owner ($currentOwner)")
                return false
            }

            val ok = bridge.stopWorkout()
            Log.i(TAG, "Workout stopped by $callerPackage")
            return ok
        }

        override fun setResistance(callerPackage: String, level: Int): Boolean {
            validateCaller(callerPackage)

            val currentOwner = bridge.getActiveOwnerPackage()
            if (currentOwner != callerPackage) {
                Log.w(TAG, "setResistance denied: $callerPackage is not the owner ($currentOwner)")
                return false
            }

            return bridge.setResistance(level)
        }

        override fun isWorkoutActive(): Boolean {
            return bridge.isWorkoutActiveValue()
        }

        override fun getActiveAppPackage(): String? {
            return bridge.getActiveOwnerPackage()
        }

        override fun getFirmwareState(): Int {
            return bridge.getFirmwareStateValue()
        }

        override fun getHeartRate(): Int {
            return bridge.getHeartRateValue()
        }

        override fun getConnectedHrmName(): String? {
            return bridge.getConnectedHrmNameValue()
        }

        override fun registerListener(listener: IWorkoutListener) {
            bridge.externalListeners.register(listener)
        }

        override fun unregisterListener(listener: IWorkoutListener) {
            bridge.externalListeners.unregister(listener)
        }
    }

    private fun validateCaller(declaredPackage: String) {
        val callingUid = Binder.getCallingUid()
        val packages = packageManager.getPackagesForUid(callingUid)
        if (packages == null || declaredPackage !in packages) {
            throw SecurityException(
                "Caller UID $callingUid does not own package $declaredPackage"
            )
        }
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            val ai = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun getCallerBinder(): IBinder? {
        // The caller's binder proxy is the IWorkoutSession connection itself
        // We use linkToDeath on it via BridgeConnectionManager
        return null // Death detection handled via registered listeners
    }
}
