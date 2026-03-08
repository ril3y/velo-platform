# FreewheelBridge (serialbridge)

Platform-signed Android service that bridges the Bowflex Velocore's serial bike controller to apps via AIDL IPC, and provides BLE heart rate monitor connectivity.

## What it does

The Velocore's resistance motor, cadence sensor, and power meter are connected to an internal MCU that communicates over `/dev/ttyS4` at 230400 baud using the Nautilus UCB (Universal Console Bus) protocol. This serial port is owned by `root:system` — only apps running as system UID can open it.

FreewheelBridge runs as `android.uid.system` (via platform key signing) and:

1. **Opens `/dev/ttyS4`** directly using JNI serial port libraries
2. **Parses UCB frames** — extracts sensor data (power, cadence, resistance, crank events) from `STREAM_NTFCN` (0x08) messages at ~1Hz
3. **Manages workout lifecycle** — sends `STREAMING_CONTROL`, `WORKOUT_BLE_DATA`, `RES_TARGET`, and `SYSTEM_HEART_BEAT` commands to the UCB
4. **Exposes AIDL service** (`IBikeService`) — any app can bind and receive real-time sensor callbacks without needing system permissions
5. **Maintains TCP bridge** on port 9999 — backward-compatible raw serial relay for legacy clients
6. **Scans for BLE heart rate monitors** — auto-connects to any device advertising HR Service (0x180D) and broadcasts BPM to all bound apps
7. **Blocks OTA updates** — applies iptables rules on boot to prevent Nautilus asset manager and RedBend OTA clients from phoning home

## Architecture

```
┌─────────────────────────────────────────────────┐
│                FreewheelBridge                  │
│                (system UID)                     │
│                                                 │
│  /dev/ttyS4 ←──── SerialPort (JNI) ────→ UCB   │
│       │                                  Parser │
│       │                                    │    │
│       ├──→ TCP:9999 (raw relay)            │    │
│       │                                    │    │
│       └──→ AIDL IBikeService ──→ Listeners │    │
│                    │                            │
│  BLE HRM ←── HrmManager ──→ onHeartRate()      │
└─────────────────────────────────────────────────┘

Consumer apps (FreeRide, BikeArcade, VeloLauncher):
  BikeServiceClient.bind()  →  onSensorData(), onHeartRate(), etc.
```

## AIDL Interface

### IBikeService

```java
// Session management (single owner at a time)
boolean claimSession(String packageName);
void releaseSession();

// Workout control (session owner only)
boolean startWorkout();
boolean stopWorkout();
boolean setResistance(int level);  // 1-100

// Keepalive — must call every 5s during active workout
void heartbeat();

// State queries
boolean isWorkoutActive();
String getSessionOwner();
int getFirmwareState();

// Listener registration
void registerListener(IBikeListener listener);
void unregisterListener(IBikeListener listener);

// Heart rate
int getHeartRate();
String getConnectedHrmName();
```

### IBikeListener

```java
void onSensorData(int resistance, int rpm, int tilt, float power,
                  long crankRevCount, int crankEventTime);
void onFirmwareStateChanged(int state, String stateName);
void onConnectionChanged(boolean connected, String message);
void onWorkoutStateChanged(boolean active, String reason);
void onHeartRate(int bpm, String deviceName);
```

## UCB Sensor Data (STREAM_NTFCN 0x08)

23-byte payload parsed from each frame:

| Offset | Size | Encoding | Field |
|--------|------|----------|-------|
| 0-3 | 4 | int32 BE | Resistance level (1-25) |
| 4-7 | 4 | int32 BE | RPM (cadence) |
| 8-11 | 4 | int32 BE | Tilt |
| 12-15 | 4 | float32 LE | Power (watts) |
| 16-19 | 4 | uint32 BE | Crank revolution count |
| 20-21 | 2 | uint16 BE | Crank event time |
| 22 | 1 | uint8 | Error code |

Sensor data arrives at **1Hz** (hardware firmware limit). Consumer apps should interpolate/animate between updates for smooth UI.

## UCB Firmware States

| Value | Name | Description |
|-------|------|-------------|
| 0 | BOOT_FAILSAFE | Recovery mode |
| 1-3 | POWER_ON_0/1/2 | Boot sequence |
| 4 | UPDATES | Checking for updates |
| 5 | TRANSITION | State transition |
| 8 | SELECTION | Idle, ready for workout |
| 9 | WORKOUT | Active workout session |
| 10 | SLEEP | Low power mode |
| 12 | SBC_DISCONNECTED | No SBC connection |

## Watchdog

If the session owner fails to call `heartbeat()` for 15 seconds (3 missed intervals), the watchdog automatically stops the workout and broadcasts `onWorkoutStateChanged(false, "watchdog_timeout")`. The client can reclaim and restart.

## Client Usage (ucblib)

Consumer apps use `BikeServiceClient` from the `ucblib` module:

```kotlin
val client = BikeServiceClient(context)
client.addListener(object : BikeServiceClient.ListenerAdapter() {
    override fun onSensorData(data: SensorData) {
        // data.power, data.rpm, data.resistanceLevel
    }
    override fun onHeartRate(bpm: Int, deviceName: String?) {
        // BLE heart rate from connected HRM
    }
    override fun onServiceConnected() {
        client.startWorkout()
    }
})
client.bind()

// Later:
client.stopWorkout()
client.unbind()
```

`BikeServiceClient` handles automatic heartbeat (every 5s while workout is active), reconnection on service disconnect, and session claim/release.

## Building

```bash
# From velo-platform root:
./gradlew :serialbridge:assembleDebug

# Sign with platform key (required for system UID):
cd serialbridge && bash sign.sh
```

## Platform Signing

FreewheelBridge must be signed with the device's platform key to run as `android.uid.system`. The signing keys are **not** committed to the repo.

### Local development

Place keys in `serialbridge/signing/`:
```
serialbridge/signing/platform.pk8
serialbridge/signing/device_platform.x509.pem
```

### CI/CD

Set environment variables:
```bash
export PLATFORM_PK8_PATH=/path/to/platform.pk8
export PLATFORM_CERT_PATH=/path/to/device_platform.x509.pem
```

Or decode from base64 secrets:
```bash
echo "$PLATFORM_PK8_B64" | base64 -d > /tmp/platform.pk8
export PLATFORM_PK8_PATH=/tmp/platform.pk8
echo "$PLATFORM_CERT_B64" | base64 -d > /tmp/device_platform.x509.pem
export PLATFORM_CERT_PATH=/tmp/device_platform.x509.pem
```

## Installing

```bash
adb -s 192.168.1.156:5555 install -r serialbridge/build/outputs/apk/serialbridge.apk
```

The service starts automatically on boot via `BootReceiver`. It also starts when any app binds to `io.freewheel.bridge.BIKE_SERVICE`.

## Versioning

Version is managed via `version.properties`:
- `VERSION_MAJOR.VERSION_MINOR.VERSION_PATCH` = display version (bump manually)
- `VERSION_BUILD` = versionCode (auto-increments on each build)
