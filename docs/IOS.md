# iOS port

The shared Kotlin (`commonMain`) — NaviLite protocol, `MirrorEngine`, and the **entire Compose UI** —
compiles and runs on iOS unchanged. The Pillion app you see on iOS is the *same* Compose UI as Android;
only how the screen is captured and streamed differs.

## Why iOS uses a Broadcast Upload Extension

iOS can only mirror the **whole** screen (Waze, Maps — any app) from a **Broadcast Upload Extension**:
a separate process that keeps capturing while Pillion is backgrounded. In-app `RPScreenRecorder` can
only see Pillion's own UI, so it can't mirror other apps. The extension also owns the dash connection,
so it keeps streaming when you switch to your nav app.

```
Compose "Start mirroring"  ──►  system broadcast picker  ──►  PillionBroadcast extension
                                                                 │ ReplayKit capture → JPEG → NaviLite
                                                                 ▼
                                              bike (External Accessory)  or  emulator (TCP)
```

The extension picks the transport automatically: the **bike** (`EAConn`, External Accessory) when a
CCU accessory advertising `com.garmin.navilite.data` is connected, otherwise the **emulator**
(`TCPConn`, the `receiver.py` TCP dash) for testing without the bike.

## Pieces

| Piece | File |
|-------|------|
| Compose UI ↔ broadcast | `iosApp/iosApp/` — `RootView` hosts the Compose UI; `BroadcastBridge` triggers the (hidden) `RPSystemBroadcastPickerView` from the Start button and relays state |
| Controller | `BroadcastMirrorController` (`iosMain`) — `start/stop` toggle the picker; `setActive` reflects the extension's state via `MirrorState.Broadcasting` |
| Extension | `iosApp/Extension/SampleHandler.swift` — ReplayKit capture → orient-fix → dash-sized JPEG → NaviLite |
| Protocol/transport | `iosApp/Shared/` — `NaviLite.swift` (matches the Kotlin codec byte-for-byte), `EAConn` (bike), `TCPConn` (emulator), behind `DashConn` |

The extension sizes its frames per-CCU: the dash rejects a JPEG that doesn't match its navigation
viewport, and the XMAX/NMAX scooter CCU uses 480×234 where the MT-class dashes use 480×240. The size
isn't advertised on the wire, so `handshake()` reads the CCU part number out of `AUTH_REQUEST_SEC_DATA`
and `NaviLite.dashSize(ccuPartNumber:)` maps it (unknown CCUs fall back to 480×240).

### Framing

The panel is a ~2:1 letterbox and no nav app is laid out for it: the turn card, ETA sheet and button
column sit around the edges, and the rider's marker is rarely centred. So the extension doesn't fit
the whole screen onto the panel — it crops a **source rectangle** and scales that. At zoom 100 the
rectangle is the largest panel-shaped one the screen holds (a portrait phone therefore gives a
full-width band instead of the ~110px strip aspect-fit used to produce); zooming shrinks it and the
two offsets slide it over the slack. There's also an unsharp-mask strength, because shrinking a phone
screen onto 480px turns street labels to mush.

All four live in Settings ▸ **Dash framing** (iOS only — see `MirrorController.supportsFraming`; the
Android paths capture at dash size already). The app republishes them to the App Group on every
slider move and the extension re-reads once a second, so the dash follows the slider live rather than
needing a stop/start per nudge. The same refresh picks up quality and frame-rate changes.

State is relayed app ⇄ extension with **Darwin notifications** (no App Group needed): the extension
posts `app.pillion.broadcast.started/stopped`; the app maps them to `MirrorState`.

### Two things that bite

- **Memory:** broadcast extensions are killed past ~50 MB. Each frame's CoreImage/JPEG encode runs in
  its own `autoreleasepool` so temporaries don't pile up at frame rate.
- **Local network:** streaming to the emulator (a LAN IP) needs Local Network permission, same as any
  iOS app — grant it under Settings ▸ Privacy ▸ Local Network.

## Build & run

```bash
brew install xcodegen                 # once
cd iosApp && xcodegen generate        # writes iosApp.xcodeproj (gitignored)
open iosApp.xcodeproj
```

Signing comes from a gitignored `iosApp/Signing.xcconfig` (set `DEVELOPMENT_TEAM`, or pick a team in
Xcode ▸ Signing & Capabilities — both the app and the `PillionBroadcast` target need it).

1. Run on a real iPhone (the extension/External Accessory don't exist on the Simulator).
2. Tap **Start mirroring** → the system broadcast sheet → **Pillion Mirror** → **Start Broadcast**.
3. Open Waze/Maps — it appears on the dash (or the emulator viewer at `http://<host>:8080`).

## What's left

- The emulator host is currently a constant in `Shared/Transport.swift` (`BroadcastConfig`). Making it
  app-configurable (and surfacing live fps) would use an **App Group** shared between app and extension.
- Confirm the bike's EA protocol string on real hardware via `EAAccessory.protocolStrings`.

## Build the shared framework only

```bash
./gradlew :composeApp:compileKotlinIosSimulatorArm64
```

The first run downloads the Kotlin/Native toolchain (large). The Android build is unaffected.
