package app.pillion.ios

import app.pillion.core.DashResolution
import app.pillion.core.Framing
import app.pillion.core.SettingsStore
import app.pillion.core.ThemeMode
import platform.Foundation.NSUserDefaults

/** [SettingsStore] backed by NSUserDefaults. */
class IosSettingsStore : SettingsStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun themeMode(): ThemeMode =
        runCatching { ThemeMode.valueOf(defaults.stringForKey(THEME_KEY) ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)

    override fun setThemeMode(mode: ThemeMode) {
        defaults.setObject(mode.name, forKey = THEME_KEY)
    }

    // Dedicated dash mode is Android-only (ADB / virtual display); iOS streams via the broadcast
    // extension, so the UI never surfaces these. They're persisted only for interface completeness.
    override fun dashEnabled(): Boolean = defaults.boolForKey(DASH_ENABLED_KEY)

    override fun setDashEnabled(enabled: Boolean) {
        defaults.setBool(enabled, forKey = DASH_ENABLED_KEY)
    }

    override fun dashResolution(): DashResolution =
        DashResolution.fromName(defaults.stringForKey(DASH_RES_KEY))

    override fun setDashResolution(resolution: DashResolution) {
        defaults.setObject(resolution.name, forKey = DASH_RES_KEY)
    }

    override fun selectedBikeId(): String? = defaults.stringForKey(BIKE_KEY)

    override fun setSelectedBikeId(id: String) {
        defaults.setObject(id, forKey = BIKE_KEY)
    }

    // Only the app's own restore-on-launch. Publishing framing to the broadcast extension is
    // BroadcastMirrorController's job — it owns the App Group the extension reads.
    override fun framing(): Framing {
        val d = Framing()
        return Framing(
            zoom = intOr(FRAMING_ZOOM_KEY, d.zoom),
            offsetX = intOr(FRAMING_OFFSET_X_KEY, d.offsetX),
            offsetY = intOr(FRAMING_OFFSET_Y_KEY, d.offsetY),
            sharpen = intOr(FRAMING_SHARPEN_KEY, d.sharpen),
        )
    }

    override fun setFraming(framing: Framing) {
        defaults.setInteger(framing.zoom.toLong(), forKey = FRAMING_ZOOM_KEY)
        defaults.setInteger(framing.offsetX.toLong(), forKey = FRAMING_OFFSET_X_KEY)
        defaults.setInteger(framing.offsetY.toLong(), forKey = FRAMING_OFFSET_Y_KEY)
        defaults.setInteger(framing.sharpen.toLong(), forKey = FRAMING_SHARPEN_KEY)
    }

    /** `integerForKey` can't tell "absent" from "0", and 0 is a valid offset — so probe the object. */
    private fun intOr(key: String, fallback: Int): Int =
        if (defaults.objectForKey(key) == null) fallback else defaults.integerForKey(key).toInt()

    private companion object {
        const val THEME_KEY = "theme_mode"
        const val DASH_ENABLED_KEY = "dash_enabled"
        const val DASH_RES_KEY = "dash_resolution"
        const val BIKE_KEY = "selected_bike_id"
        const val FRAMING_ZOOM_KEY = "framing_zoom"
        const val FRAMING_OFFSET_X_KEY = "framing_offset_x"
        const val FRAMING_OFFSET_Y_KEY = "framing_offset_y"
        const val FRAMING_SHARPEN_KEY = "framing_sharpen"
    }
}
