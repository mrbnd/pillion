package app.pillion.core

import kotlinx.coroutines.flow.StateFlow

/**
 * What the UI needs from a mirroring session. Each platform entrypoint supplies the implementation
 * (wiring permissions + the platform ByteChannel/ScreenSource), so the Compose UI stays platform-agnostic.
 */
interface MirrorController {
    val state: StateFlow<MirrorState>
    fun start(settings: MirrorSettings)
    fun stop()

    /**
     * Whether this controller can reframe the captured screen ([MirrorSettings.framing]). Only the
     * iOS broadcast extension composes the panel image from a full phone screen; the Android paths
     * capture at the dash size already, so their UI must not offer a control that does nothing.
     */
    val supportsFraming: Boolean get() = false

    /**
     * Push settings to a session that is already running, so framing and quality can be dialled in
     * while watching the dash instead of stopping and restarting the stream for every nudge.
     * Default no-op: controllers that only read settings at [start] simply ignore it.
     */
    fun applyLiveSettings(settings: MirrorSettings) {}
}
