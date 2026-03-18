# FreeWheel Jailbreak (freewheel-jailbreak)

> **Status: Under active development — not ready for public use.**
> The `freewheel.exe` binary is not included in releases yet. If you're interested in testing, reach out first.

Windows desktop application that jailbreaks Bowflex Velocore fitness bikes. One-click network discovery, pre-flight validation, 11-step automated jailbreak, and full stock restoration — no command-line tools or Android SDK required.

## What it does

The Velocore runs Android 9 in kiosk mode, locked to the Nautilus/JRNY app. FreeWheel replaces the stock software stack with the velo-platform apps, enabling third-party fitness apps, bike sensor access, and full system control.

### Jailbreak Sequence (11 steps)

| Step | Operation |
|------|-----------|
| 1 | Connect to device via ADB over WiFi |
| 2 | Verify ADB shell access |
| 3 | Disable JRNY, AppMonitor, OTA updates |
| 4 | Check current device state |
| 5 | Install FreewheelBridge (platform-signed, owns serial port) |
| 6 | Install VeloLauncher (set as default HOME) |
| 7 | Install FreeRide (fitness app) |
| 8 | Install BikeArcade (games app) |
| 9 | Install Jailbreak overlay APK |
| 10 | Apply system settings (enable navbar, disable Chrome/Play Services, ADB persistence) |
| 11 | Final setup (start overlay, verify TCP:9999 bridge) |

If any step fails, the tool automatically restores the device to stock.

### Restore Sequence (10 steps)

Fully reverses the jailbreak — re-enables JRNY, NautilusLauncher, OTA, and removes all velo-platform apps.

## Architecture

```
freewheel-jailbreak/
├── cmd/freewheel/main.go         — Entry point
├── internal/
│   ├── ui/
│   │   ├── app.go                — Fyne GUI (device scan, buttons, log)
│   │   └── logger.go             — Color-coded thread-safe log widget
│   ├── device/
│   │   ├── scan.go               — Network scanner (ADB port 5555)
│   │   ├── preflight.go          — 11-point compatibility validation
│   │   ├── jailbreak.go          — Jailbreak sequence with auto-restore
│   │   ├── restore.go            — Stock restoration sequence
│   │   └── types.go              — Logger interface
│   ├── update/
│   │   └── update.go             — GitHub Releases API, APK caching, self-update
│   └── adb/
│       └── adb.go                — Native ADB wire protocol (no external tools)
└── assets/
    ├── embed.go                  — Logo embed + APK lookup (cache/local overrides)
    └── logo.png                  — Application icon
```

## Native ADB Implementation

FreeWheel implements the ADB wire protocol directly — no `adb.exe` or Android SDK needed. Handles CNXN/AUTH handshake, RSA key generation (`~/.android/adbkey`), shell commands, and file push/pull with the sync protocol.

| Method | Purpose |
|--------|---------|
| `Connect(host, port)` | ADB connection with auth handshake |
| `Shell(cmd)` | Run shell command, return output |
| `Push(local, remote)` | Upload file (64KB chunks) |
| `Install(apk)` | Push APK + `pm install -r` |
| `IsADBPort(ip)` | Lightweight port probe for scanning |

## APK Management

APKs are downloaded at runtime from GitHub Releases and cached locally in `%APPDATA%/freewheel/apks/`. On startup, FreeWheel checks for the latest release and downloads any new or updated APKs. Local file overrides are also supported for development.

## Pre-flight Checks

Before jailbreaking, validates:

- Android version = 9
- Board = RK3399/Rockchip
- JRNY (com.nautilus.bowflex.usb) is installed
- Platform signing key SHA-1 matches expected value
- Warns if JRNY version differs from tested v2.25.1

## Building

Requires Go 1.25+, MSYS64/MinGW64 (for CGO/Fyne), and optionally `go-winres` for Windows icon embedding.

```bash
# Build:
cd freewheel-jailbreak
go build -ldflags "-H windowsgui -X main.version=dev -X main.gitHash=$(git rev-parse --short=8 HEAD)" -o freewheel.exe ./cmd/freewheel
```

CI builds are handled by GitHub Actions — the release workflow builds APKs and the Windows binary in parallel, then publishes a GitHub Release with all artifacts.

## System Settings Applied

The jailbreak applies these Android settings:

| Setting | Value | Purpose |
|---------|-------|---------|
| `navigationbar_switch` | 1 | Enable navbar (exit kiosk) |
| `stay_on_while_plugged_in` | 3 | Screen stays on |
| `wifi_sleep_policy` | 2 | WiFi never sleeps |
| `screen_off_timeout` | 1800000 | 30-min screen timeout |
| `persist.adb.tcp.port` | 5555 | ADB persistence across reboot |
| Chrome, Play Services, GSF | disabled | Save RAM on 2GB device |
