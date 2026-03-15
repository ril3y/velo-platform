# velofit

Client library for VeloLauncher's fitness ContentProvider API. Provides target power computation, effort zone classification, ride logging, and user fitness config — all backed by the launcher's user profile database.

## Classes

### VeloFitnessClient

Main entry point. Queries VeloLauncher's ContentProvider for fitness data and logs rides.

```java
VeloFitnessClient client = new VeloFitnessClient(context);

// Check if launcher is reachable
boolean available = client.isAvailable();

// Get user's fitness config (cached after first call)
FitnessConfig config = client.getFitnessConfig();
// config.ftp, config.maxHeartRate, config.weightLbs, config.age

// Get target power range for a resistance level
PowerTarget target = client.getTargetPower(15);
// target.targetLow, target.targetHigh, target.centerPower

// With difficulty multiplier (scales resistance before lookup)
PowerTarget scaled = client.getTargetPower(15, 1.5f);

// Log a completed ride
RideStats stats = new RideStats.Builder()
    .startTime(startTimeMs)
    .durationSeconds(elapsed)
    .calories(cal)
    .avgPower(avgW)
    .avgRpm(rpm)
    .avgResistance(res)
    .maxPower(maxW)
    .distanceMiles(dist)
    .avgSpeedMph(speed)
    .avgHeartRate(hr)
    .source("io.freewheel.freeride", "FreeRide")
    .workout(workoutId, workoutName)  // optional
    .build();
client.logRide(stats);
```

Falls back to local computation with default FTP (120W) if VeloLauncher is not installed.

### PowerTarget

Target power range for a resistance level, with helper methods for effort bar rendering:

```java
PowerTarget target = client.getTargetPower(resistance);

// Classify actual power into effort zone
PowerTarget.EffortZone zone = target.zone(actualPower);
// IDLE, UNDER, ON_TARGET, OVER

// Bar rendering helpers (all return 0.0-1.0 fractions)
float ratio = target.complianceRatio(actualPower);
float zoneLow = target.zoneLowFraction();
float zoneHigh = target.zoneHighFraction();
```

### FitnessConfig

Immutable snapshot of user fitness profile:

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `ftp` | int | 120 | Functional Threshold Power (watts) |
| `maxHeartRate` | int | 180 | Max heart rate (BPM) |
| `weightLbs` | int | 170 | Body weight |
| `age` | int | 35 | Age (years) |

### RideStats

Ride data for logging. Built with `RideStats.Builder`:

| Field | Type | Description |
|-------|------|-------------|
| `startTime` | long | Epoch millis |
| `durationSeconds` | int | Total ride time |
| `calories` | int | Estimated calories burned |
| `avgPower` / `maxPower` | int | Average and peak watts |
| `avgRpm` | int | Average cadence |
| `avgResistance` | int | Average resistance level |
| `avgSpeedMph` / `distanceMiles` | float | Speed and distance |
| `avgHeartRate` | int | Average BPM |
| `sourcePackage` / `sourceLabel` | String | Originating app |
| `workoutId` / `workoutName` | String | Structured workout reference (optional) |

## Distribution

Like ucblib, velofit is used two ways:

1. **Gradle module dependency** — in-repo apps (VeloLauncher, FreeRide) use `implementation(project(":velofit"))`
2. **Standalone JAR** — for consumer apps outside this monorepo

```bash
./gradlew :velofit:buildClassesJar
# Output: velofit/build/libs/velofit.jar

cp velofit/build/libs/velofit.jar ../bikearcade/app/libs/
```

## ContentProvider URIs

velofit queries the following URIs on `io.freewheel.launcher.provider`:

| URI | Used by |
|-----|---------|
| `content://.../fitness_config` | `getFitnessConfig()` |
| `content://.../target_power/{n}` | `getTargetPower(n)` |
| `content://.../rides` | `logRide()` (INSERT) |
| `content://.../profile` | Internal (profile lookup) |
| `content://.../workouts` | Available for future use |
