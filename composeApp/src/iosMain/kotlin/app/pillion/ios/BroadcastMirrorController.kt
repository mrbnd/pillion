package app.pillion.ios

import app.pillion.core.MirrorController
import app.pillion.core.MirrorSettings
import app.pillion.core.MirrorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults

/**
 * Backs the Pillion "Start mirroring" button on iOS with the ReplayKit Broadcast Upload Extension.
 *
 * iOS can only mirror the *whole* screen (any app, e.g. Waze) from a broadcast extension, which runs
 * out of process and keeps going while Pillion is backgrounded. So this controller doesn't stream
 * itself: [start]/[stop] just trigger the system broadcast picker — the Swift shell injects that as
 * [onToggle] — and the extension does the capture + NaviLite streaming. The extension posts Darwin
 * notifications on start/finish, which the Swift shell relays here via [setActive] so the shared UI
 * reflects live state without the app needing the extension's fps.
 */
class BroadcastMirrorController : MirrorController {
    /** Set by the Swift shell: shows the system broadcast picker (start or stop). */
    var onToggle: (() -> Unit)? = null

    private val _state = MutableStateFlow<MirrorState>(MirrorState.Idle)
    override val state: StateFlow<MirrorState> = _state.asStateFlow()

    override fun start(settings: MirrorSettings) {
        publish(settings)
        onToggle?.invoke()
    }

    override fun stop() { onToggle?.invoke() }

    /** The broadcast extension owns the capture, so it is the only thing that can crop the frame. */
    override val supportsFraming: Boolean get() = true

    /**
     * Republish while the broadcast is running. The extension re-reads the App Group once a second,
     * so dragging a framing slider redraws the dash instead of needing a stop/start round trip.
     */
    override fun applyLiveSettings(settings: MirrorSettings) = publish(settings)

    /**
     * Hand the settings to the out-of-process broadcast extension via the shared App Group — it
     * can't read the app's own UserDefaults.
     */
    private fun publish(settings: MirrorSettings) {
        NSUserDefaults(suiteName = APP_GROUP)?.apply {
            setInteger(settings.maxFps.toLong(), forKey = "stream.maxFps")
            setInteger(settings.quality.toLong(), forKey = "stream.quality")
            setInteger(settings.framing.zoom.toLong(), forKey = "frame.zoom")
            setInteger(settings.framing.offsetX.toLong(), forKey = "frame.offsetX")
            setInteger(settings.framing.offsetY.toLong(), forKey = "frame.offsetY")
            setInteger(settings.framing.sharpen.toLong(), forKey = "frame.sharpen")
        }
    }

    private companion object {
        const val APP_GROUP = "group.app.pillion"
    }

    /** Called by the Swift shell from the extension's broadcast start/finish Darwin notifications. */
    fun setActive(active: Boolean) {
        _state.value = if (active) MirrorState.Broadcasting else MirrorState.Idle
    }
}
