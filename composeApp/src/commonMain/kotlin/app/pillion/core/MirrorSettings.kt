package app.pillion.core

/**
 * How the captured screen is fitted onto the dash panel.
 *
 * The panel is a ~2:1 letterbox, so a phone screen never lands on it well: nav apps park their turn
 * card, ETA sheet and button column around the edges, and the position marker is rarely centred.
 * Rather than guess a crop per app, the source rectangle is user-tunable — set once on the bike,
 * watching the dash, and never touched again.
 *
 * @param zoom    size of the source rectangle, 100 = the largest panel-shaped rectangle that fits
 *                the screen (no black bars), 300 = a third of that in each direction.
 * @param offsetX where that rectangle sits horizontally: -100 flush left, 0 centred, 100 flush right.
 * @param offsetY the same vertically, in screen terms: -100 top, 0 centred, 100 bottom.
 * @param sharpen unsharp-mask strength applied after the downscale (0–100). Shrinking a phone screen
 *                onto 480 px turns street labels to mush; a little sharpening buys back legibility
 *                at the cost of some bytes per frame.
 */
data class Framing(
    val zoom: Int = 100,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val sharpen: Int = 50,
)

/**
 * User-tunable session settings. Lower values trade smoothness for battery, heat and bandwidth.
 *
 * @param quality JPEG quality of each frame (10–80).
 * @param maxFps  upper bound on frames sent per second; the engine paces to this.
 * @param dashResolution off-screen display size for dedicated dash mode; output is scaled to 480x240.
 * @param framing how the captured screen is cropped onto the panel (see [Framing]).
 */
data class MirrorSettings(
    val quality: Int = 40,
    val maxFps: Int = 15,
    val dashResolution: DashResolution = DashResolution.DEFAULT,
    val framing: Framing = Framing(),
)
