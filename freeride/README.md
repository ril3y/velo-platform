# FreeRide

Real-time fitness ride app for the Bowflex Velocore console. Displays live power, cadence, resistance, heart rate, calories, speed, and distance during workouts. Supports free rides (user-controlled) and structured workouts (segment-based coaching with target resistance).

## Modes

### Free Ride

Standalone full-screen ride UI. User controls resistance manually, app displays real-time metrics with gradient backgrounds and animated transitions.

### Structured Workout

Follows a workout plan from VeloLauncher — segments with target resistance, duration, and coaching messages. Shows current segment progress, effort zone compliance (under/on-target/over), and a difficulty multiplier for scaling intensity.

### Overlay Mode

When launched with a media app intent, FreeRide runs as a floating overlay service on top of the media app. Metrics are rendered as a translucent panel via `WindowManager`, allowing users to watch instructional videos while tracking ride data.

## Architecture

```
RideActivity
  ├── RideViewModel
  │     └── RideSessionManager
  │           ├── WorkoutSessionClient (velofit) — AIDL to VeloLauncher for bike control
  │           ├── VeloFitnessClient (velofit) — ride logging, power targets, user profile
  │           └── Metrics computation (calories, speed, distance)
  ├── FreeRideScreen (Compose) — free ride UI
  ├── WorkoutRideScreen (Compose) — structured workout UI
  └── OverlayService — floating overlay for media mode

RideSummaryActivity
  └── RideSummaryScreen (Compose) — post-ride stats
```

### Metrics Computation

- **Speed**: `speed_mph = cbrt(power / 4.0) * 2.24` (cube-root power model)
- **Distance**: integrated from speed over time deltas
- **Calories**: estimated from average power and duration

## Integration

FreeRide integrates with the velo-platform ecosystem through two velofit clients:

- **WorkoutSessionClient** — binds to VeloLauncher's `IWorkoutSession` AIDL service for real-time sensor data and resistance control
- **VeloFitnessClient** — queries the launcher's ContentProvider for user fitness config (FTP, max HR) and logs completed rides (>= 60s) to ride history

### Launcher Discovery

FreeRide registers as a workout app via manifest meta-data:

```xml
<meta-data android:name="io.freewheel.workout_app" android:value="true" />
<meta-data android:name="io.freewheel.workout_description"
           android:value="Free ride with real-time power, cadence, and heart rate" />
```

VeloLauncher discovers it and can launch it with an optional workout JSON payload via intent extra.

### Launch Intents

| Intent | Description |
|--------|-------------|
| `io.freewheel.freeride.ACTION_START_RIDE` | Start a free ride |
| `io.freewheel.freeride.ACTION_START_RIDE` + `workout_json` extra | Start a structured workout |
| `io.freewheel.freeride.ACTION_START_RIDE` + `media_package` extra | Start in overlay mode over a media app |

## Permissions

| Permission | Reason |
|------------|--------|
| `INTERNET` | ContentProvider and fitness API access |
| `SYSTEM_ALERT_WINDOW` | Overlay rendering on top of media apps |
| `FOREGROUND_SERVICE` | Overlay service persistence |
| `WRITE_SECURE_SETTINGS` | Immersive mode policy control |

## Dependencies

- **velofit** — module dependency for `WorkoutSessionClient` and `VeloFitnessClient`
- **Jetpack Compose** — Material 3 UI
- **Haze** — glassmorphism / backdrop blur effects

## Building

```bash
# From velo-platform root:
./gradlew :freeride:assembleDebug

# Install:
adb -s 192.168.1.156:5555 install -r freeride/build/outputs/apk/debug/freeride-debug.apk
```

## Versioning

Version is managed via `version.properties`:
- `VERSION_MAJOR.VERSION_MINOR.VERSION_PATCH` = display version (bump manually)
- `VERSION_BUILD` = versionCode (auto-increments on each build)
