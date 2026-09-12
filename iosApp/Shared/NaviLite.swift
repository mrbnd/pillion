import Foundation
import CoreGraphics

/// Swift NaviLite codec for the broadcast extension (a memory-limited process where linking the full
/// Compose/Kotlin framework is undesirable). This mirrors the canonical Kotlin `NaviLiteCodec`
/// byte-for-byte — same CRC-32/MPEG-2, framing, echo-auth, and setup burst. See docs/PROTOCOL.md.
enum NaviLite {
    static let FT_PHONE: UInt8 = 6

    private static let T: [UInt32] = {
        var t = [UInt32](repeating: 0, count: 256)
        for i in 0..<256 {
            var c = UInt32(i) << 24
            for _ in 0..<8 { c = (c & 0x80000000) != 0 ? ((c << 1) ^ 0x04C11DB7) : (c << 1) }
            t[i] = c & 0xffffffff
        }
        return t
    }()

    static func crc32(_ d: [UInt8]) -> UInt32 {
        var c: UInt32 = 0xFFFFFFFF
        for b in d { c = ((c << 8) ^ T[Int(((c >> 24) ^ UInt32(b)) & 0xff)]) & 0xffffffff }
        return c
    }

    /// Build a full on-wire frame: magic | ver | ft | svc | size(4 LE) | pdt | crc(4 LE) | payload.
    static func frame(_ ft: UInt8, _ svc: UInt8, _ pdt: UInt8, _ payload: [UInt8]) -> [UInt8] {
        let size = payload.count
        let h: [UInt8] = [0x6e, 0x41, 0x6c, 0x40, 1, ft, svc,
            UInt8(size & 0xff), UInt8((size >> 8) & 0xff), UInt8((size >> 16) & 0xff), UInt8((size >> 24) & 0xff), pdt]
        var hp = h; hp.append(contentsOf: payload)
        let crc = crc32(hp)
        var out = h
        out.append(UInt8(crc & 0xff)); out.append(UInt8((crc >> 8) & 0xff))
        out.append(UInt8((crc >> 16) & 0xff)); out.append(UInt8((crc >> 24) & 0xff))
        out.append(contentsOf: payload)
        return out
    }

    /// Echo-auth: the dash sends a SEC_DATA seed; the phone replies with its last 4 bytes XOR 0x0a.
    static func secDataAckPayload(_ seed: [UInt8]) -> [UInt8] {
        let n = 4
        return (0..<n).map { seed[seed.count - n + $0] ^ 0x0a }
    }

    static func partNumber(_ seed: [UInt8]) -> String {
        String(bytes: seed[0..<max(0, seed.count - 4)].map { $0 ^ 0x0a }, encoding: .ascii) ?? "?"
    }

    /// The standard Yamaha 480x240 TFT (MT-07 · MT-09 · XSR900 · R9 · Tracer) — the default.
    static let defaultDashSize = CGSize(width: 480, height: 240)

    /// Native pixel size of the dash's navigation viewport, resolved from the CCU part number the
    /// handshake de-obfuscates. The dash renders each JPEG into a fixed-size viewport and rejects a
    /// frame whose dimensions don't match, showing "Connection Error" on entering navigation.
    /// Unknown part numbers fall back to `defaultDashSize`, so a new bike still connects.
    /// Mirrors the Kotlin `NaviLiteDisplay` used by the Android path.
    static func dashSize(ccuPartNumber: String) -> CGSize {
        // XMAX / NMAX scooter CCU (006-B3952-xx) renders navigation at 480x234, not 480x240.
        ccuPartNumber.hasPrefix("006-B3952") ? CGSize(width: 480, height: 234) : defaultDashSize
    }

    static func hexB(_ s: String) -> [UInt8] {
        var r = [UInt8](); var i = s.startIndex
        while i < s.endIndex { let j = s.index(i, offsetBy: 2); r.append(UInt8(s[i..<j], radix: 16)!); i = j }
        return r
    }
}
