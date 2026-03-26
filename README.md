# velo-platform

Monorepo for the Freewheel platform — custom Android software for the Bowflex Velocore exercise bike console (Rockchip SoC, Android 9, 1920x1080).

Replaces the stock Nautilus/JRNY software with an open framework: a serial bridge service, a fitness platform launcher, and libraries that let any Android app read bike sensors, control workouts, and integrate with the launcher's ride tracking.

## Modules

| Module | Type | Package | Description |
|--------|------|---------|-------------|
| [**launcher**](launcher/) | Android App | `io.freewheel.launcher` | VeloLauncher — fitness platform with workouts, free ride, media overlay, ride history, OTA updates, resistance calibration |
| [**freewheelbridge**](freewheelbridge/) | Android App | `io.freewheel.bridge` | Platform-signed service — bridges `/dev/ttyS4` serial (UCB protocol) to AIDL IPC + TCP:9999, BLE HRM, OTA firmware |
| [**ucblib**](ucblib/) | Android Library | `io.freewheel.ucb` | Low-level client library — AIDL definitions, `BikeServiceClient`, UCB protocol, sensor data, OTA/calibration, RidePhysics |
| [**velofit**](velofit/) | Android Library | `io.freewheel.fit` | Fitness app SDK — `WorkoutSessionClient` for real-time bike control, `VeloFitnessClient` for profile/rides/workouts |

See also:
- [**FreeWheel**](https://github.com/ril3y/freewheel) — One-click jailbreak tool (Windows, Go)
- [**BikeArcade**](https://github.com/ril3y/bike-arcade) — Retro arcade games controlled by pedaling
- [**Developer Guide**](DEVELOPER.md) — Build fitness apps for the platform

## Architecture

```
 ┌──────────────────────────────────────────────────────┐
 │                  FreewheelBridge                      │
 │  /dev/ttyS4 ←→ UCB Protocol ←→ AIDL + TCP:9999      │
 │  BLE HRM scan ←→ onHeartRate()                       │
 │  OTA flash, resistance calibration                   │
 │  (platform-signed, android.uid.system)               │
 └───────────────────────┬──────────────────────────────┘
                         │ AIDL IPC (IBikeService / IBikeListener)
                         │
 ┌───────────────────────┴──────────────────────────────┐
 │               VeloLauncher (launcher)                 │
 │                                                       │
 │  BridgeConnectionManager  — persistent connection,    │
 │    owns BikeServiceClient, exposes StateFlows,        │
 │    broadcasts to external listeners                   │
 │                                                       │
 │  WorkoutSessionService  — IWorkoutSession AIDL,       │
 │    session ownership, caller validation,              │
 │    crash detection (linkToDeath + 3s grace)           │
 │                                                       │
 │  WorkoutAppRegistry  — discovers apps with            │
 │    io.freewheel.workout_app meta-data                 │
 │                                                       │
 │  RideSessionManager  — pure data accumulator,         │
 │    computes speed/distance/calories, saves rides      │
 │                                                       │
 │  RideOverlayService  — floating stats overlay         │
 │    (power, RPM, HR, time) on top of media apps        │
 │                                                       │
 │  VeloContentProvider  — workouts, rides, profile,     │
 │    session state, sensor snapshot, workout import      │
 │                                                       │
 │  Room DB  — rides, user_profile, workouts             │
 │    (built-in seeded from assets, imported from apps)  │
 └──────────┬───────────────────────────┬───────────────┘
            │ IWorkoutSession AIDL      │ ContentProvider
            │                           │
 ┌──────────┴───────────────────────────┴───────────────┐
 │                     velofit SDK                       │
 │                                                       │
 │  WorkoutSessionClient — bind, requestStart/Stop,      │
 │    setResistance, receive sensor data via AIDL        │
 │                                                       │
 │  VeloFitnessClient — power targets, ride logging,     │
 │    session state, sensor snapshot, workout import      │
 │                                                       │
 │  IWorkoutSession.aidl / IWorkoutListener.aidl         │
 │  FitnessConfig, PowerTarget, RideStats                │
 │  SessionState, SensorSnapshot                         │
 └──────────────────────┬──────────────────────────────┘
                        │ Standalone JAR or Gradle dependency
 ┌──────────────────────┴──────────────────────────────┐
 ┌──────────────────────┴──────────────────────────────┐
 │             FreeRide + External Workout Apps         │
 │  FreeRide (in-repo), BikeArcade, custom games       │
 │  Register with: io.freewheel.workout_app meta-data  │
 └─────────────────────────────────────────────────────┘

 ┌─────────────────────────────────────────────────────┐
 │            FreeWheel Jailbreak (Windows)             │
 │  Native ADB → network scan → deploy all APKs        │
 │  11-step jailbreak / 10-step restore                │
 │  Single .exe with embedded APKs (~160 MB)           │
 └─────────────────────────────────────────────────────┘
```

### Data flow during a workout

```
Bike MCU ──serial──→ FreewheelBridge ──AIDL──→ VeloLauncher (BridgeConnectionManager)
  ↑                       │                        │
  └── SET_RESISTANCE ←────┘                        ├──→ RideSessionManager (stats accumulation)
       (0x09, 1-100)                               ├──→ IWorkoutSession AIDL ──→ External App
                                                   ├──→ ContentProvider ──→ VeloFitnessClient queries
                                                   └──→ RideOverlayService (floating stats)
```

1. FreewheelBridge reads `/dev/ttyS4` at 230400 baud, accumulates UCB frames (STX/ETX delimited, hex-encoded, CRC32 validated)
2. Parses `STREAM_NTFCN` (0x08) at ~12 Hz during workout → broadcasts `onSensorData()` via AIDL
3. VeloLauncher's `BridgeConnectionManager` receives all callbacks, updates StateFlows, and rebroadcasts to registered external listeners
4. External apps receive sensor data via `WorkoutSessionClient` (AIDL) or poll via `VeloFitnessClient` (ContentProvider)
5. Only the session owner can call `setResistance()` — launcher validates caller UID on every mutating call

### Two app modes

1. **Structured** — app follows a launcher-defined workout plan (segments with target resistance/duration). App queries the plan and sets resistance per segment.
2. **Dynamic/game-driven** — app drives resistance in real-time based on gameplay (`setResistance(50)` on uphill, etc.). No predefined plan. The game IS the workout.

Both use the same `IWorkoutSession` AIDL interface.

## Building a Workout App

External apps integrate with the bike through the `velofit` SDK. The launcher owns the bridge connection — apps request workout sessions through it.

### 1. Add the library

**If your app is in this monorepo** (Gradle module dependency):
```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":velofit"))
}
```

**If your app is external** (standalone JAR):
```bash
# Build the JAR (includes AIDL-generated classes)
./gradlew :velofit:buildClassesJar

# Copy to your project
cp velofit/build/libs/velofit.jar ../yourapp/app/libs/
```

### 2. Register as a workout app

Add meta-data to your `AndroidManifest.xml` so the launcher discovers your app:
```xml
<application>
    <meta-data android:name="io.freewheel.workout_app" android:value="true" />
    <meta-data android:name="io.freewheel.workout_description"
               android:value="Arcade cycling game with dynamic resistance" />
</application>
```

Your app will appear in the launcher's workout app picker.

### 3. Connect and start a workout

```java
import io.freewheel.fit.WorkoutSessionClient;

WorkoutSessionClient session = new WorkoutSessionClient(context);

session.addListener(new WorkoutSessionClient.ListenerAdapter() {
    @Override
    public void onSensorData(int resistance, int rpm, int tilt, float power,
                              long crankRevCount, int crankEventTime) {
        // Called at ~12 Hz during workout
        gameEngine.setPlayerSpeed(rpm);
        updatePowerDisplay(power);
    }

    @Override
    public void onHeartRate(int bpm, String deviceName) {
        updateHeartRateDisplay(bpm);
    }

    @Override
    public void onWorkoutStateChanged(boolean active, String reason) {
        if (!active) {
            // Launcher stopped the workout (user returned home, watchdog, etc.)
            showRideSummary();
        }
    }

    @Override
    public void onServiceConnected() {
        // VeloLauncher is bound — ready to start
        session.requestStart();
    }
});

// Bind to VeloLauncher's WorkoutSession service (auto-reconnects)
session.bind();
```

### 4. Control resistance

```java
// Game-driven resistance (dynamic mode)
session.setResistance(50);  // uphill
session.setResistance(10);  // downhill

// Or follow structured workout segments:
session.setResistance(currentSegment.resistance);
```

### 5. Use power targets (optional)

```java
import io.freewheel.fit.VeloFitnessClient;
import io.freewheel.fit.PowerTarget;

VeloFitnessClient fitness = new VeloFitnessClient(context);

// Get target power zone for current resistance level
PowerTarget target = fitness.getTargetPower(15);
PowerTarget.EffortZone zone = target.zone(actualPower);  // IDLE, UNDER, ON_TARGET, OVER

// Scale difficulty (0.5 = easier, 1.5 = harder)
PowerTarget scaled = fitness.getTargetPower(15, 1.2f);
```

### 6. Log the ride and stop

```java
import io.freewheel.fit.RideStats;

// Log ride to VeloLauncher's history
fitness.logRide(new RideStats.Builder()
    .durationSeconds(1800)
    .calories(350)
    .avgPower(185).maxPower(320)
    .avgRpm(78)
    .avgSpeedMph(18.5f).distanceMiles(9.2f)
    .avgResistance(12).avgHeartRate(145)
    .source("com.yourapp", "Your App Name")
    .build());

// Stop and clean up
session.requestStop();
session.unbind();
```

### 7. Import custom workouts (optional)

```java
// Import a structured workout into the launcher's database
String workoutJson = "{ \"name\": \"Hill Climb\", \"durationMinutes\": 30, ... }";
Uri uri = fitness.importWorkout(workoutJson);
```

### Important rules

- **Session ownership**: Only one app can own a workout at a time. `requestStart()` returns `false` if another app is active. The launcher validates caller UID on every mutating call.
- **Crash detection**: If your app dies mid-workout, the launcher detects via `linkToDeath()` and auto-stops after a 3-second grace period.
- **No direct bridge access needed**: External apps use `WorkoutSessionClient` (AIDL to launcher), not `BikeServiceClient` (AIDL to bridge). The launcher owns the bridge connection.
- **Sensor data only during workout**: The UCB only streams `STREAM_NTFCN` in firmware state WORKOUT (0x09). In SELECTION state (0x08), sensors return zeros.
- **FreewheelBridge + VeloLauncher must be installed**: The bridge talks to hardware; the launcher manages sessions.

## Direct Bridge Access (Advanced)

For apps that need low-level bridge access (diagnostics, OTA, calibration), use `BikeServiceClient` from `ucblib` directly. This bypasses the launcher's session management.

```java
import io.freewheel.ucb.BikeServiceClient;
import io.freewheel.ucb.SensorData;

BikeServiceClient client = new BikeServiceClient(context);
client.addListener(new BikeServiceClient.ListenerAdapter() {
    @Override public void onSensorData(SensorData data) { /* ... */ }
    @Override public void onServiceConnected() { client.startWorkout(); }
});
client.bind();

// Control resistance, calibration, OTA, raw frames...
client.setResistance(50);
client.startCalibration(0);
client.setRawFrameMonitoring(true);

client.stopWorkout();
client.unbind();
```

**Note**: Direct bridge access conflicts with launcher-managed sessions. Use `WorkoutSessionClient` for normal workout apps.

## ContentProvider URIs

The launcher's ContentProvider (`content://io.freewheel.launcher.provider/`) exposes:

| URI | Methods | Description |
|-----|---------|-------------|
| `/workouts` | query, insert | All workouts (built-in + imported) |
| `/workouts/{id}` | query | Single workout with full segment JSON |
| `/rides` | query, insert | Ride history |
| `/rides/{id}` | query | Single ride |
| `/profile` | query | User profile (name, weight, FTP, etc.) |
| `/target_power/{resistance}` | query | Target power range for resistance level |
| `/fitness_config` | query | FTP, max HR, weight, age |
| `/session` | query | Current session state (active, owner, elapsed) |
| `/session/sensor` | query | Latest sensor snapshot (resistance, rpm, power, HR) |

```bash
# Query session state
adb shell content query --uri content://io.freewheel.launcher.provider/session

# Query live sensor data
adb shell content query --uri content://io.freewheel.launcher.provider/session/sensor

# Query workouts
adb shell content query --uri content://io.freewheel.launcher.provider/workouts
```

## SensorData Fields

Data received in `onSensorData()` at ~12 Hz during WORKOUT state:

| Field | Type | Source | Description |
|-------|------|--------|-------------|
| `resistanceLevel` | int | UCB bytes 0-3, BE | Current resistance (1-25) |
| `rpm` | int | UCB bytes 4-7, BE | Pedal cadence |
| `tilt` | int | UCB bytes 8-11, BE | Velocore lean angle |
| `power` | float | UCB bytes 12-15, **LE** | Watts (computed by firmware) |
| `crankRevCount` | long | UCB bytes 16-19, BE | Cumulative crank revolutions |
| `crankEventTime` | int | UCB bytes 20-21, BE | Last crank event timestamp (1/1024s) |
| `error` | int | UCB byte 22 | Error code (0 = OK) |
| `heartRate` | int | BLE HRM | BPM (0 if no HRM connected) |
| `hrmDeviceName` | String | BLE HRM | Connected device name |

**Note**: Mixed endianness in the UCB frame — integers are big-endian, power (float) is little-endian.

## UCB Firmware States

| Value | Name | Description |
|-------|------|-------------|
| 0 | BOOT_FAILSAFE | Recovery mode |
| 1-3 | POWER_ON_0/1/2 | Boot sequence |
| 4 | UPDATES | Checking for updates |
| 5 | TRANSITION | State transition |
| 6 | MFG | Manufacturing / calibration mode |
| 7 | OTA | Firmware update in progress |
| 8 | SELECTION | Idle, ready for workout |
| 9 | WORKOUT | Active — sensor streaming enabled |
| 10 | SLEEP | Low power |
| 11 | RESET | Rebooting |
| 12 | SBC_DISCONNECTED | No console connection |

## Advanced: Resistance Calibration

3-point calibration via MFG test mode (available in VeloLauncher Settings > Calibration):

```java
// Enter calibration mode
client.startCalibration(0);

// Listen for instructions
client.addListener(new BikeServiceClient.ListenerAdapter() {
    @Override
    public void onCalibrationProgress(int step, String instruction) {
        // step 0: "Turn resistance knob to minimum (zero). Press Confirm."
        // step 1: "Turn resistance knob to maximum. Press Confirm."
        // step 2: "Turn resistance knob to center detent. Press Confirm."
        showInstruction(instruction);
    }

    @Override
    public void onCalibrationComplete(boolean success) {
        // Calibration finished
    }
});

// User positions knob, then:
client.confirmCalibrationStep();

// Or abort:
client.cancelCalibration();
```

## Advanced: OTA Firmware Flash

Flash UCB firmware via the bridge (available in VeloLauncher Settings > UCB Firmware):

```java
// Open firmware .hex or .bin file
ParcelFileDescriptor pfd = context.getContentResolver()
    .openFileDescriptor(firmwareUri, "r");

// Start flash (5-phase: permission → erase → write → validate → reboot)
client.getService().startOtaFlash(pfd);

client.addListener(new BikeServiceClient.ListenerAdapter() {
    @Override
    public void onOtaProgress(int phase, int blockCurrent, int blockTotal) {
        // phase: 0=permission, 1=erase, 2=write, 3=validate, 4=reboot
        updateProgressBar(blockCurrent, blockTotal);
    }

    @Override
    public void onOtaComplete(boolean success, String error) {
        // Flash done (or failed with error message)
    }
});

// Cancel if needed (WARNING: canceling during ERASE phase corrupts firmware)
client.getService().cancelOtaFlash();
```

## Advanced: Raw UCB Protocol

For debugging or implementing custom protocol commands:

```java
// Enable raw frame monitoring
client.setRawFrameMonitoring(true);

client.addListener(new BikeServiceClient.ListenerAdapter() {
    @Override
    public void onRawFrame(byte[] frame, boolean isOutgoing) {
        String hex = bytesToHex(frame);
        Log.d("UCB", (isOutgoing ? "TX" : "RX") + ": " + hex);
    }
});

// Send a raw UCB command (already framed with STX/ETX/CRC)
client.sendRawCommand(rawBytes);
```

## Building

```bash
# Build all Android modules
./gradlew assembleDebug

# Build individual modules
./gradlew :launcher:assembleDebug
./gradlew :freeride:assembleDebug
./gradlew :freewheelbridge:assembleDebug
./gradlew :ucblib:assembleRelease
./gradlew :velofit:assembleRelease

# Build standalone JARs for external apps
bash build-libs.sh
# or:
./gradlew :ucblib:buildClassesJar :velofit:buildClassesJar

# Sign freewheelbridge with platform key
cd freewheelbridge && bash sign.sh

# Build FreeWheel jailbreak tool (Windows)
cd freewheel-jailbreak && go build -ldflags "-H windowsgui" -o freewheel.exe ./cmd/freewheel
# Or full build (rebuilds APKs + embeds them):
cd freewheel-jailbreak/build && build.bat
```

## Installing

```bash
# VeloLauncher
adb -s 192.168.1.156:5555 install -r launcher/build/outputs/apk/debug/launcher-debug.apk

# FreeRide
adb -s 192.168.1.156:5555 install -r freeride/build/outputs/apk/debug/freeride-debug.apk

# FreewheelBridge (must be platform-signed first)
adb -s 192.168.1.156:5555 install -r freewheelbridge/build/outputs/apk/freewheelbridge.apk

# Or use FreeWheel jailbreak tool to deploy everything at once
```

## Versioning

Each app module has a `version.properties` file:
```
VERSION_MAJOR=1
VERSION_MINOR=0
VERSION_PATCH=0
VERSION_BUILD=1
```

- `versionName` = `MAJOR.MINOR.PATCH` (bump manually for releases)
- `versionCode` = `VERSION_BUILD` (auto-increments after every successful build)

## Target Device

- **Hardware**: Bowflex Velocore console — Rockchip RK3399, 2GB RAM, 1920x1080 LCD
- **OS**: Android 9 (API 28)
- **ADB**: `adb -s 192.168.1.156:5555`
- **Architecture**: arm64-v8a
- **Serial**: `/dev/ttyS4` at 230400 baud (UCB protocol to bike MCU)

## Platform Signing

FreewheelBridge requires `android.uid.system` to access `/dev/ttyS4`. See [freewheelbridge/README.md](freewheelbridge/README.md) for signing setup.

## Key Constants

| Constant | Value | Notes |
|----------|-------|-------|
| Serial device | `/dev/ttyS4` | UCB to bike MCU |
| Baud rate | 230400 | |
| TCP port | 9999 | Raw frame relay + legacy clients |
| Bridge AIDL action | `io.freewheel.bridge.BIKE_SERVICE` | FreewheelBridge binding |
| Bridge AIDL package | `io.freewheel.bridge` | FreewheelBridge package |
| Session AIDL action | `io.freewheel.launcher.WORKOUT_SESSION` | WorkoutSessionService binding |
| Session AIDL package | `io.freewheel.launcher` | VeloLauncher package |
| ContentProvider | `io.freewheel.launcher.provider` | VeloFit queries + session state |
| Workout app meta-data | `io.freewheel.workout_app` | App registration flag |
| Heartbeat interval | 5s | Client → bridge |
| Watchdog timeout | 15s | Bridge kills workout if no heartbeat |
| Crash grace period | 3s | Launcher auto-stops after app death |
| System heartbeat | 3s | Bridge → UCB firmware |
