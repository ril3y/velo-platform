# Keep Room entities
-keep class io.freewheel.launcher.data.RideRecord { *; }

# Keep UCB library
-keep class io.battlewithbytes.ucb.** { *; }

# Keep AIDL interfaces (used by ucblib BikeServiceClient)
-keep class io.freewheel.bridge.** { *; }
