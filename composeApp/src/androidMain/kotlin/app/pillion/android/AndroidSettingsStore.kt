package app.pillion.android

import android.content.Context
import app.pillion.core.DashResolution
import app.pillion.core.Framing
import app.pillion.core.SettingsStore
import app.pillion.core.ThemeMode

/** [SettingsStore] backed by SharedPreferences. Single responsibility: persist preferences. */
class AndroidSettingsStore(context: Context) : SettingsStore {
    private val prefs = context.getSharedPreferences("pillion.settings", Context.MODE_PRIVATE)

    override fun themeMode(): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)

    override fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    override fun dashEnabled(): Boolean = prefs.getBoolean(KEY_DASH_ENABLED, false)

    override fun setDashEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DASH_ENABLED, enabled).apply()
    }

    override fun dashResolution(): DashResolution =
        DashResolution.fromName(prefs.getString(KEY_DASH_RESOLUTION, null))

    override fun setDashResolution(resolution: DashResolution) {
        prefs.edit().putString(KEY_DASH_RESOLUTION, resolution.name).apply()
    }

    override fun selectedBikeId(): String? = prefs.getString(KEY_BIKE, null)

    override fun setSelectedBikeId(id: String) {
        prefs.edit().putString(KEY_BIKE, id).apply()
    }

    // Framing is an iOS-only control (see MirrorController.supportsFraming); persisted here for
    // interface completeness so the Android UI never surfaces it.
    override fun framing(): Framing = Framing(
        zoom = prefs.getInt(KEY_FRAMING_ZOOM, Framing().zoom),
        offsetX = prefs.getInt(KEY_FRAMING_OFFSET_X, Framing().offsetX),
        offsetY = prefs.getInt(KEY_FRAMING_OFFSET_Y, Framing().offsetY),
        sharpen = prefs.getInt(KEY_FRAMING_SHARPEN, Framing().sharpen),
    )

    override fun setFraming(framing: Framing) {
        prefs.edit()
            .putInt(KEY_FRAMING_ZOOM, framing.zoom)
            .putInt(KEY_FRAMING_OFFSET_X, framing.offsetX)
            .putInt(KEY_FRAMING_OFFSET_Y, framing.offsetY)
            .putInt(KEY_FRAMING_SHARPEN, framing.sharpen)
            .apply()
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_DASH_ENABLED = "dash_enabled"
        const val KEY_DASH_RESOLUTION = "dash_resolution"
        const val KEY_BIKE = "selected_bike_id"
        const val KEY_FRAMING_ZOOM = "framing_zoom"
        const val KEY_FRAMING_OFFSET_X = "framing_offset_x"
        const val KEY_FRAMING_OFFSET_Y = "framing_offset_y"
        const val KEY_FRAMING_SHARPEN = "framing_sharpen"
    }
}
