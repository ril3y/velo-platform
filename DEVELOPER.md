# VeloLauncher Fitness App Developer Guide

Build fitness apps for the Bowflex Velocore bike using the VeloLauncher platform.

## Quick Start

1. Add `velofit.jar` to your app's `libs/` directory
2. Register as a fitness app in your `AndroidManifest.xml`
3. Use `WorkoutSessionClient` to get sensor data
4. Use `VeloFitnessClient` to log rides and show summaries
5. Build and install alongside VeloLauncher

## Platform Architecture

```
┌──────────────────────────────────────────────────────────┐
│                      Your Fitness App                     │
│                                                           │
│  WorkoutSessionClient ──────┐                             │
│  VeloFitnessClient ────────┤ (velofit.jar)               │
│                              │                             │
└──────────────────────────────┼─────────────────────────────┘
                          AIDL IPC
┌──────────────────────────────┼─────────────────────────────┐
│                      VeloLauncher                          │
│                              │                             │
│  WorkoutSessionService ◄─────┘                             │
│         │                                                  │
│  BridgeConnectionManager                                   │
│         │                                                  │
│  BikeServiceClient (AIDL) ──────► FreewheelBridge Service  │
│                                          │                 │
│                                   UCB Serial Protocol      │
│                                          │                 │
│                                   Bowflex Velocore Bike    │
└────────────────────────────────────────────────────────────┘
```

## 1. Register as a Fitness App

Add these `<meta-data>` tags inside your `<application>` in `AndroidManifest.xml`:

```xml
<application ...>
    <!-- Required: Makes your app appear in the launcher's FITNESS section -->
    <meta-data android:name="app.category.primary" android:value="fitness" />

    <!-- Required: Registers with WorkoutAppRegistry for session management -->
    <meta-data android:name="io.freewheel.workout_app" android:value="true" />

    <!-- Optional: Description shown in the launcher -->
    <meta-data android:name="io.freewheel.workout_description"
               android:value="Your app description here" />
</application>
```

## 2. Get Sensor Data (WorkoutSessionClient)

`WorkoutSessionClient` binds to VeloLauncher's WorkoutSession AIDL service. It provides real-time bike sensor data, session ownership, and resistance control.

### Setup

```kotlin
val session = WorkoutSessionClient(context)
session.addListener(object : WorkoutSessionClient.ListenerAdapter() {

    override fun onSensorData(resistance: Int, rpm: Int, tilt: Int, power: Float,
                              crankRevCount: Long, crankEventTime: Int) {
        // Called ~1-2 Hz when in WORKOUT state
        // resistance: 1-100 (bike resistance level)
        // rpm: pedal cadence
        // tilt: lean angle (left/right on VeloCore)
        // power: instantaneous watts (Float)
        // crankRevCount: cumulative crank revolutions (for CSC profile)
        // crankEventTime: timestamp of last crank event
    }

    override fun onHeartRate(bpm: Int, deviceName: String?) {
        // Heart rate from connected BLE HRM
        // bpm: beats per minute (0 if no HRM connected)
        // deviceName: BLE device name or null
    }

    override fun onServiceConnected() {
        // AIDL service is bound — safe to call requestStart()
        session.requestStart()
    }

    override fun onServiceDisconnected() {
        // Service unbound (launcher killed or restarting)
    }

    override fun onWorkoutStateChanged(active: Boolean, reason: String?) {
        // active: true when your app owns the session, false when stopped
        // reason: human-readable reason for state change
        // If !active and you didn't request stop, another app took the session
    }

    override fun onConnectionChanged(connected: Boolean, message: String?) {
        // Bike hardware connection state
        // connected: true when FreewheelBridge is talking to the UCB
    }

    override fun onFirmwareStateChanged(state: Int, stateName: String?) {
        // UCB firmware state machine (0=BOOT, 8=SELECTION, 9=WORKOUT, etc.)
    }
})
```

### Lifecycle

```kotlin
// App start: bind to the service
session.bind()

// After onServiceConnected: claim the session
session.requestStart()  // Returns true if granted

// During workout: read sensor data via callbacks
// Optionally control resistance:
session.setResistance(15)  // 1-100

// App end: release the session
session.requestStop()
session.unbind()
```

### Important Notes

- **Call `requestStart()` inside `onServiceConnected()`**, not right after `bind()`. The AIDL service isn't available until the callback fires.
- **Only one app can own the session at a time.** If another app calls `requestStart()`, your `onWorkoutStateChanged(false)` fires.
- **Sensor data only flows in WORKOUT state.** The UCB must transition from SELECTION (8) to WORKOUT (9) before power/RPM data streams.
- **The launcher detects crashes.** If your app dies without calling `requestStop()`, the launcher reclaims the session after a 3-second grace period.

## 3. Log Rides (VeloFitnessClient)

After a workout completes, log it so it appears in the launcher's ride history.

```kotlin
val fitnessClient = VeloFitnessClient(context)

val stats = RideStats.Builder()
    .startTime(startTimeMillis)
    .durationSeconds(elapsedSeconds)
    .calories(totalCalories)
    .avgPower(avgWatts)
    .maxPower(peakWatts)
    .avgRpm(avgCadence)
    .avgResistance(avgResistance)
    .avgHeartRate(avgBpm)
    .distanceMiles(totalMiles)
    .avgSpeedMph(avgSpeed)
    .source("com.yourapp.package", "Your App Name")
    .workout("workout-id", "Workout Name")  // optional
    .build()

fitnessClient.logRide(stats)
```

- Rides are stored in the launcher's Room database via ContentProvider
- They appear in the launcher's Ride History screen
- The `source` field identifies your app in the history
- Only log rides >= 60 seconds (short rides clutter the history)

## 4. Show Ride Summary Screen

After logging a ride, show the launcher's summary screen for a consistent post-ride experience:

```kotlin
// One-liner using the SDK helper:
VeloFitnessClient.showRideSummary(context, stats, "Your App Name")
```

Or manually via intent:

```kotlin
val intent = Intent("io.freewheel.launcher.SHOW_RIDE_SUMMARY").apply {
    putExtra("durationSeconds", elapsed)
    putExtra("calories", cals)
    putExtra("avgPower", avgPower)
    putExtra("maxPower", maxPower)
    putExtra("avgRpm", avgRpm)
    putExtra("avgResistance", avgRes)
    putExtra("avgHeartRate", avgHr)
    putExtra("avgSpeedMph", avgSpeed)
    putExtra("distanceMiles", distance)
    putExtra("workoutName", "Your Workout Name")
}
startActivity(intent)  // Launch from Activity context, not Application
```

**Important:** Launch from your Activity (not Application context) so the "Done" button returns to your app.

The summary screen shows duration, calories, power, RPM, heart rate, speed, and distance in a consistent UI.

## 5. Read User Profile

Get the user's fitness profile (FTP, weight, age) from the launcher:

```kotlin
val fitnessClient = VeloFitnessClient(context)
val config = fitnessClient.fitnessConfig

val ftp = config.ftp           // Functional Threshold Power (watts)
val maxHr = config.maxHeartRate
val weight = config.weightLbs
```

This reads from the launcher's ContentProvider at `content://io.freewheel.launcher.provider/fitness_config`.

### Write User Profile

If your app has a user setup flow, write the profile back so it's shared:

```kotlin
val values = ContentValues().apply {
    put("weightLbs", 175)
    put("age", 35)
    put("ftp", 200)
    put("maxHeartRate", 185)
}
contentResolver.update(
    Uri.parse("content://io.freewheel.launcher.provider/profile"),
    values, null, null
)
```

## 6. Power Targets and Zones

Use FTP-calibrated power targets for difficulty scaling:

```kotlin
val fitnessClient = VeloFitnessClient(context)

// Get target power range for a resistance level
val target = fitnessClient.getTargetPower(resistanceLevel)
// target.targetLow, target.targetHigh — watt range
// target.zone(actualPower) — returns EffortZone.UNDER, ON_TARGET, or OVER
// target.complianceRatio(actualPower) — 0.0 to 1.0+ ratio
```

## 7. Permissions

Your app needs these permissions:

```xml
<!-- Network access for AIDL service binding -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- Optional: Bluetooth for BLE heart rate monitors -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<!-- Optional: Storage for user data -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

**Note:** `ACCESS_FINE_LOCATION` must be granted at runtime on Android 9+ for Bluetooth scanning. Grant via ADB for development: `adb shell pm grant com.yourapp.package android.permission.ACCESS_FINE_LOCATION`

## 8. ContentProvider URIs

The launcher exposes data via ContentProvider (`content://io.freewheel.launcher.provider/`):

| URI | Method | Description |
|-----|--------|-------------|
| `/workouts` | GET | All workout definitions |
| `/workouts/{id}` | GET | Single workout with segments |
| `/rides` | GET | Ride history |
| `/rides` | INSERT | Log a new ride |
| `/profile` | GET | User profile |
| `/profile` | UPDATE | Update user profile |
| `/fitness_config` | GET | FTP, max HR, weight |
| `/session` | GET | Current ride session state |
| `/session/sensor` | GET | Latest sensor snapshot |

## 9. Example: Minimal Fitness App

```kotlin
class MyFitnessActivity : ComponentActivity() {

    private lateinit var session: WorkoutSessionClient
    private val fitnessClient by lazy { VeloFitnessClient(this) }
    private var startTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        session = WorkoutSessionClient(this)
        session.addListener(object : WorkoutSessionClient.ListenerAdapter() {
            override fun onServiceConnected() {
                session.requestStart()
                startTime = System.currentTimeMillis()
            }
            override fun onSensorData(resistance: Int, rpm: Int, tilt: Int,
                                      power: Float, crankRevCount: Long, crankEventTime: Int) {
                // Update your UI with live data
                runOnUiThread { updateDisplay(power, rpm, resistance) }
            }
            override fun onHeartRate(bpm: Int, deviceName: String?) {
                runOnUiThread { updateHeartRate(bpm) }
            }
        })
        session.bind()

        // Your UI setup here...
    }

    fun endWorkout() {
        val elapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt()

        session.requestStop()
        session.unbind()

        if (elapsed >= 60) {
            val stats = RideStats.Builder()
                .startTime(startTime)
                .durationSeconds(elapsed)
                .source(packageName, "My Fitness App")
                .build()

            fitnessClient.logRide(stats)
            VeloFitnessClient.showRideSummary(this, stats, "My Fitness App")
        }

        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        session.requestStop()
        session.unbind()
    }
}
```

## 10. Testing

### On the bike:
```bash
adb connect <bike-ip>:5555
adb install -r your-app.apk
# Grant runtime permissions if needed:
adb shell pm grant com.yourapp.package android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.yourapp.package android.permission.READ_EXTERNAL_STORAGE
```

### Verify integration:
- Your app appears in the launcher's FITNESS section
- Sensor data flows when pedaling
- Rides show in launcher's Ride History after completing a workout
- Ride summary screen shows after ending a workout >= 60 seconds

## SDK Reference

### WorkoutSessionClient
| Method | Description |
|--------|-------------|
| `bind()` | Bind to VeloLauncher's WorkoutSession service |
| `unbind()` | Unbind from the service |
| `requestStart()` | Claim the workout session (returns boolean) |
| `requestStop()` | Release the workout session |
| `setResistance(level)` | Set bike resistance (1-100, only if session owner) |
| `isWorkoutActive()` | Check if a workout is currently active |
| `isBound()` | Check if service is bound |
| `getHeartRate()` | Get current heart rate |
| `getFirmwareState()` | Get UCB firmware state |
| `addListener(listener)` | Register for callbacks |
| `removeListener(listener)` | Unregister callbacks |

### VeloFitnessClient
| Method | Description |
|--------|-------------|
| `logRide(stats)` | Log a completed ride to launcher history |
| `getFitnessConfig()` | Get user's FTP, max HR, weight |
| `getTargetPower(resistance)` | Get FTP-calibrated power target |
| `showRideSummary(ctx, stats, label)` | Show the ride summary screen (static) |

### RideStats.Builder
| Method | Description |
|--------|-------------|
| `startTime(millis)` | Ride start timestamp |
| `durationSeconds(secs)` | Total ride duration |
| `calories(cal)` | Estimated calories burned |
| `avgPower(watts)` | Average power |
| `maxPower(watts)` | Peak power |
| `avgRpm(rpm)` | Average cadence |
| `avgResistance(level)` | Average resistance |
| `avgHeartRate(bpm)` | Average heart rate |
| `distanceMiles(miles)` | Total distance |
| `avgSpeedMph(mph)` | Average speed |
| `source(package, label)` | Your app's package + display name |
| `workout(id, name)` | Optional workout identifier |
