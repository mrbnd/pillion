import Foundation

/// A NaviLite frame read off the wire.
struct NaviFrame { let svc: Int; let payload: [UInt8] }

/// A bidirectional NaviLite byte link the broadcast extension streams over — implemented by the bike
/// (`EAConn`, External Accessory) and the dev emulator (`TCPConn`, plain TCP). The extension is
/// transport-agnostic, exactly like the shared engine on the app side.
protocol DashConn: AnyObject {
    var logger: ((String) -> Void)? { get set }
    func connect() throws
    func write(_ bytes: [UInt8])
    func readFrame(timeout: TimeInterval) throws -> NaviFrame
    func close()
}

/// How the captured screen is cropped onto the dash panel. Mirrors the Kotlin `Framing` the app
/// publishes to the App Group; see `sourceRect(for:)` for what the numbers mean geometrically.
struct DashFraming: Equatable {
    /// 100 = the largest panel-shaped rectangle the screen holds; 300 = a third of it each way.
    var zoom = 100
    /// Where that rectangle sits, in screen terms: -100 flush left, 0 centred, 100 flush right.
    var offsetX = 0
    /// The same vertically: -100 top, 0 centred, 100 bottom.
    var offsetY = 0
    /// Unsharp-mask strength applied after the downscale, 0–100.
    var sharpen = 50
}

/// Where the extension streams. The bike is preferred when present; otherwise the dev emulator.
enum BroadcastConfig {
    static let dashProtocol = "com.garmin.navilite.data"
    /// Dev fallback: the NaviLite receiver's TCP dash. Used when no bike accessory is connected.
    /// Set this to your emulator host's IP when testing without a bike.
    static let emulatorHost = "127.0.0.1"
    static let emulatorPort: UInt16 = 7220
    /// Fallback frame-rate cap when no live setting is available (see [liveMaxFps]).
    static let maxFps: Int = 15

    /// App Group shared with the container app so the extension can read the user's live Settings
    /// (the extension is a separate process and can't see the app's own UserDefaults).
    static let appGroup = "group.app.pillion"
    private static var shared: UserDefaults? { UserDefaults(suiteName: appGroup) }

    // Each reader falls back to a safe default if the group is unavailable (e.g. a re-signer that
    // didn't carry the entitlement) — so the stream still works, just not slider-driven. `object`
    // rather than `integer`, because 0 is a legitimate offset and must be told apart from "unset".
    private static func int(_ key: String, _ valid: ClosedRange<Int>, or fallback: Int) -> Int {
        guard let v = shared?.object(forKey: key) as? Int, valid.contains(v) else { return fallback }
        return v
    }

    static func liveMaxFps() -> Int { int("stream.maxFps", 5...30, or: maxFps) }

    /// App stores JPEG quality as 10…80; map to CoreImage's 0…1.
    static func liveJpegQuality() -> Double { Double(int("stream.quality", 10...80, or: 40)) / 100.0 }

    static func liveFraming() -> DashFraming {
        let d = DashFraming()
        return DashFraming(zoom: int("frame.zoom", 100...300, or: d.zoom),
                           offsetX: int("frame.offsetX", -100...100, or: d.offsetX),
                           offsetY: int("frame.offsetY", -100...100, or: d.offsetY),
                           sharpen: int("frame.sharpen", 0...100, or: d.sharpen))
    }
}
