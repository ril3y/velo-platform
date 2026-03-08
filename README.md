# velo-platform

Monorepo for the Freewheel platform — custom Android software for the Bowflex Velocore exercise bike console (Rockchip SoC, Android 9, 1920x1080).

## Modules

| Module | Type | Package | Description |
|--------|------|---------|-------------|
| **launcher** | Android App | `io.freewheel.launcher` | VeloLauncher — replacement HOME launcher with workout picker, ride history, app management |
| **serialbridge** | Android App | `io.freewheel.bridge` | FreewheelBridge — platform-signed service that bridges `/dev/ttyS4` serial to AIDL + TCP, adds BLE HRM |
| **ucblib** | Android Library | `io.freewheel.ucb` | UCB client library — AIDL client for FreewheelBridge, sensor data, lean sensor |
| **velofit** | Android Library | `io.freewheel.fit` | VeloFit — fitness API client for user profile, target power, ride logging |

## Dependency Graph

```
                   ┌──────────────┐
                   │  serialbridge │ (system UID, platform-signed)
                   │  /dev/ttyS4  │
                   │  BLE HRM     │
                   └──────┬───────┘
                          │ AIDL IPC
                   ┌──────┴───────┐
                   │    ucblib     │ (BikeServiceClient)
                   └──────┬───────┘
              ┌───────────┼───────────┐
              │           │           │
        ┌─────┴─────┐ ┌──┴──┐  ┌─────┴─────┐
        │  launcher  │ │Free │  │ BikeArcade │
        │            │ │Ride │  │            │
        └─────┬──────┘ └──┬──┘  └────────────┘
              │           │
        ┌─────┴───────────┴──┐
        │      velofit        │ (ContentProvider client)
        └─────────────────────┘
```

- **launcher** depends on **ucblib** as a Gradle module dependency
- **serialbridge** is standalone (owns the AIDL definitions)
- **ucblib** and **velofit** also produce standalone JARs for consumer apps outside this monorepo (BikeArcade, FreeRide)

## Building

```bash
# Build everything
./gradlew assembleDebug

# Build individual modules
./gradlew :launcher:assembleDebug
./gradlew :serialbridge:assembleDebug
./gradlew :ucblib:assembleRelease
./gradlew :velofit:assembleRelease

# Build standalone JARs for consumer apps
bash build-libs.sh
# or:
./gradlew :ucblib:buildClassesJar :velofit:buildClassesJar

# Sign serialbridge with platform key
cd serialbridge && bash sign.sh
```

## Installing

```bash
# VeloLauncher
adb -s 192.168.1.156:5555 install -r launcher/build/outputs/apk/debug/launcher-debug.apk

# FreewheelBridge (must be platform-signed first)
adb -s 192.168.1.156:5555 install -r serialbridge/build/outputs/apk/serialbridge.apk
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

- **Hardware**: Bowflex Velocore console — Rockchip SoC, 2GB RAM, 1920x1080 LCD
- **OS**: Android 9 (API 28)
- **ADB**: `adb -s 192.168.1.156:5555`
- **Architecture**: arm64-v8a

## Platform Signing

The serialbridge module requires platform key signing (`android.uid.system`) to access `/dev/ttyS4`. See [serialbridge/README.md](serialbridge/README.md) for signing setup.
