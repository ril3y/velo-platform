package io.freewheel.fit;

/**
 * Snapshot of the current workout session state, queried from VeloLauncher's ContentProvider.
 */
public class SessionState {
    public final boolean active;
    public final String ownerPackage;
    public final String ownerLabel;
    public final long startTime;
    public final int elapsedSeconds;

    public SessionState(boolean active, String ownerPackage, String ownerLabel,
                        long startTime, int elapsedSeconds) {
        this.active = active;
        this.ownerPackage = ownerPackage;
        this.ownerLabel = ownerLabel;
        this.startTime = startTime;
        this.elapsedSeconds = elapsedSeconds;
    }
}
