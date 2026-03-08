# VeloLauncher

Replacement HOME launcher for the Bowflex Velocore console. Replaces the stock Bowflex/JRNY launcher with a custom Jetpack Compose UI that supports third-party apps, structured workouts, ride history, and system management.

## Features

- **Home screen** — fitness app tiles, recent rides widget, system status indicators (bridge, overlay, WiFi, RAM)
- **Workout picker** — browse structured workouts by category, select media app, start workout with overlay
- **Ride history** — Room database of all rides (from any app via ContentProvider), per-ride stats, CSV export
- **App drawer** — launch any installed app, recent apps section, long-press for app info/uninstall
- **Task manager** — view running processes, kill apps, RAM usage
- **Setup wizard** — first-run user profile (name, age, weight, height) for FTP/HR zone calculations
- **Screen saver** — configurable dim timeout and screen-off to prevent LCD burn-in, with burn-in pixel shift
- **Service monitor** — checks FreewheelBridge and overlay health, auto-restart if configured
- **Boot receiver** — starts monitoring services on device boot

## ContentProvider API

VeloLauncher exposes a ContentProvider (`io.freewheel.launcher.provider`) that other apps query for shared fitness data:

| URI | Method | Description |
|-----|--------|-------------|
| `/profile` | query | User profile (name, weight, age, FTP, maxHR) |
| `/fitness_config` | query | FTP + maxHR with defaults if profile incomplete |
| `/target_power/{resistance}` | query | Target power range for resistance level (uses FTP ±15%) |
| `/workouts` | query | List all workouts |
| `/workouts/{id}` | query | Full workout with segments JSON |
| `/rides` | query | All ride records |
| `/rides` | insert | Log a new ride (used by velofit client library) |

### Power Target Formula

```
effortFraction = 0.10 + (resistance - 1) × (0.90 / 24)
centerPower = FTP × effortFraction
targetLow = centerPower × 0.85
targetHigh = centerPower × 1.15
```

Default FTP when user hasn't set one: `weightKg × 1.5` W/kg (recreational rider), minimum 80W.

Max HR default: Tanaka formula `208 - 0.7 × age`.

## Data Model

- **UserProfile** — Room entity: displayName, weightLbs, heightInches, age, gender, ftp, maxHeartRate
- **RideRecord** — Room entity: startTime, duration, calories, avgRpm, avgPower, maxPower, avgSpeed, distance, avgResistance, avgHeartRate, source, workoutId
- **Workout** — JSON from assets: id, name, description, category, coach, segments[], optionalMedia, color

## Dependencies

- **ucblib** — module dependency for `BikeServiceClient` (bike sensor data during rides)
- **Jetpack Compose** — Material 3 UI
- **Room** — ride history and user profile database
- **Haze** — glassmorphism/backdrop blur effects
- **Coil** — app icon loading

## Building

```bash
# From velo-platform root:
./gradlew :launcher:assembleDebug

# Install:
adb -s 192.168.1.156:5555 install -r launcher/build/outputs/apk/debug/launcher-debug.apk
```

To set as default HOME launcher after install, select VeloLauncher when prompted or use:
```bash
adb shell cmd package set-home-activity io.freewheel.launcher/.MainActivity
```
