# Pillion — iOS scooter-CCU fork

Fork of [alexandrevega/pillion](https://github.com/alexandrevega/pillion) (`origin` =
`mrbnd/pillion`). Goal: make Pillion's **iOS** path work on a Yamaha **scooter** Garmin CCU, then
make the picture on that dash actually good. Working branch: `feat/ios-framing-and-quality`.

## Read this first

**On iOS, the shared Kotlin engine does not stream to the bike.** `MirrorEngine`, `Handshake`,
`ScreenSource` and `NaviLiteDisplay` in `commonMain` are used by exactly one caller — Android's
`CaptureService`. The iOS bike path is 100% Swift in the ReplayKit broadcast extension:

- `iosApp/Extension/SampleHandler.swift` — handshake, capture, framing, JPEG, send loop
- `iosApp/Shared/NaviLite.swift` — codec, deliberately mirroring the Kotlin one byte-for-byte
- `iosApp/Shared/EAConn.swift` — the bike transport (MFi External Accessory / iAP2)

`docs/IOS.md` claims the shared engine "runs on iOS unchanged". For the bike stream that is
misleading. Changing Kotlin protocol code does nothing on iOS. Upstream PR #8 fixes the same dash-size
bug for Android and is **not** portable here — `cefcd92` is its iOS counterpart.

Kotlin still owns the whole Compose UI and the settings, which reach the extension through the
`group.app.pillion` App Group (the two are separate processes).

## What's done, and what's actually verified

| Commit | What | Status |
|---|---|---|
| `cefcd92` | Dash size per-CCU: part number `006-B3952*` → 480×234, else 480×240 | Ridden, fixed "Connection Error" |
| `6c08e92` | Dash framing sliders, unsharp mask, pace-relative adaptive quality | Ridden, "works well" |

Neither commit was ever compiled by the author (written on a Linux box with no Xcode and no JDK);
both were proven only by building on a Mac and riding.

The bike reports part number `006-B3952*` — inferred, because `cefcd92` gates the 480×234 mapping on
that prefix and turning it on is what fixed the dash. Confirm from the log line if it ever matters.

## Open items, in the order worth attacking

1. **The Sharpness, Image quality and Max frame rate sliders feel like they do nothing** (rider
   report, framing sliders work fine, so the App Group plumbing itself is proven). Strong hypothesis:
   the adaptive controller in `pushLoop` is pinned at its floor. If so, `q` never climbs to the
   user's ceiling (`q = min(jpegQuality, q + 0.02)`), the fps cap never binds because the link is the
   limiter, and sharpening is dead by construction — `encode()` has `if detail < 1.0 { soften } else
   if framing.sharpen > 0 { sharpen }`, so a soft-limiting link skips the sharpen branch forever.
   **Check the per-second log line before changing anything**: `q0.12 d0.6` confirms it. Then decide
   between loosening the thresholds further, raising the 0.12 floor, or making sharpening independent
   of softening.
2. **Dash→phone commands are silently dropped.** `awaitAck` keeps only service 80 and discards the
   rest unlogged, so services 55 (start content update), 53 (go home), 48 (start route) and 51/52
   (zoom) go unanswered. A rider pressing "Go home" on the dash gets an error — expected today, not a
   regression, and identical on Android/MT-07. One log line there reveals what the dash is asking for.
3. **Scooter-panel overscan / safe area** — the upstream PR #8 author flagged edge clipping on this
   panel and left it out of scope.
4. **Native turn-by-turn** (services 4/19) instead of mirroring JPEGs: the dash renders its own crisp
   arrow, street name and distance, which is how Garmin StreetCross works and the only real answer to
   "I want lines, not a shrunken phone screen". Big feature, needs a routing source.

## Build and deploy on a Mac

```bash
brew install xcodegen
cd iosApp && xcodegen generate && open iosApp.xcodeproj   # iosApp.xcodeproj is gitignored
```

- Needs full Xcode, JDK 17, **and the Android SDK** — `composeApp/build.gradle.kts` has an `android {}`
  block that Gradle configures even for an iOS-only build, so without `local.properties`
  (`sdk.dir=…`) it fails with "SDK location not found".
- Signing comes from a gitignored `iosApp/Signing.xcconfig` (`DEVELOPMENT_TEAM`), or pick a team in
  Xcode. **Both** targets need it: `iosApp` and `PillionBroadcast`.
- First Gradle run downloads the Kotlin/Native toolchain; it is large and slow.
- Running straight onto the device from Xcode is now the fast loop. `iosApp/build-ipa.sh` produces an
  unsigned IPA for AltStore/Sideloadly — only needed for handing a build to someone else.
- A broadcast extension is killed past ~50 MB, and neither it nor External Accessory exists on the
  Simulator. Test on real hardware.

## Diagnosing

The extension is a separate process, so its logs do **not** appear in Xcode's debug console. Use
Console.app (or `log stream`) filtered on subsystem `app.pillion.ext`. Logging goes through
`os_log` with `%{public}` precisely so `log collect` doesn't redact it.

```
PillionExt: settings — fps=15 quality=0.4 zoom=100 offset=0,0 sharpen=50
PillionExt: CCU part=006-B3952-03 dash=480x234      ← mapping fired
PillionExt: FPS 11.3  14KB  ack 170ms  q0.40 d1.0   ← every second; q and d are the whole story
```

`q` at 0.12 and/or `d` at 0.6 means the link is saturated and the controller has shed everything it
can. `q` sitting at the user's setting with `d1.0` means there is headroom to spend.

## Protocol facts worth not re-deriving

- Auth is a de-obfuscate-and-echo challenge, XOR `0x0A`, no per-bike key. See `docs/PROTOCOL.md`.
- The dash image size is **never advertised on the wire**. The only signal is the CCU part number
  inside `AUTH_REQUEST_SEC_DATA`. The mapping comes from the "Vehicle Model Detection" table in
  `docs/PROTOCOL.md` (decompiled from Garmin StreetCross): `006-B3952` → 480×234,
  `006-B4160`/`006-B4920` → 480×240.
- A JPEG whose dimensions don't match makes the dash spin for a few seconds and then show
  **"Connection Error"** on entering Navigation. That was the original bug.
- Only one app can hold the link at a time — close Garmin StreetCross first.

## Field notes from the rider

- iOS cannot lock landscape (rotation lock is portrait-only). **Guided Access** freezes the current
  orientation and is what makes this usable on a moving bike; set its Display Auto-Lock to Never.
- Framing exists because no nav app can be told to hide its turn card, ETA sheet or button column,
  and Google Maps deliberately pushes the rider's marker off-centre to make room for them.
