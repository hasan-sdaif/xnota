package com.xnota.core.stroke

import kotlin.math.sin

/**
 * Calligraphy nib-angle lock (the fork's `nib_angle_lock` preference): by default the nib's broad
 * face follows the direction of travel — that is what makes calligraphy calligraphy — but a
 * locked angle holds the nib at one fixed world orientation, which turns the tool into a chisel
 * marker or an edged pen held deliberately askew.
 *
 * [directionTerm] is the whole of the mixing logic, pure and pinned by tests: −1 (or any negative)
 * follows travel (the historical tangent-y term), a value in `[0, 360]` holds
 * `sin(angle)` — the same component of a unit vector at that angle the travelling nib reads.
 */
object NibAngle {
    /** The sentinel for "follow the travel direction". */
    const val FOLLOW_TRAVEL = -1.0

    fun isLocked(lockDeg: Double): Boolean = lockDeg >= 0.0

    /**
     * The nib's direction term for one sample: the tangent's y-component when following travel,
     * or `sin(lockDeg°)` when locked. Angles are screen-world, y-down, clockwise from +x: 90° is
     * the broad face pointing down-screen, 0° the thin edge.
     */
    fun directionTerm(tangentY: Double, lockDeg: Double): Double =
        if (isLocked(lockDeg)) sin(Math.toRadians(lockDeg % 360.0)) else tangentY
}

/**
 * One bundle of everything the ink pipeline can be tuned with beyond the per-stroke
 * [com.xnota.core.tools.ToolConfig]: the position/pressure filter choice, adaptive smoothing, the
 * pressure shape, grain and scatter, and the nib-angle lock. Defaults are the historical identity
 * everywhere, so [StrokeEngine.build] called without options is byte-for-byte the old pipeline
 * (the spec's conformance vectors rely on exactly that).
 */
data class InkEngineOptions(
    /** Position+pressure low-pass selection (the `stroke_filter` preference). */
    val filter: StrokeFilter = StrokeFilter.DEFAULT,
    /** Speed-scaled low-pass lag (the `adaptive_ink_smoothing` preference). */
    val adaptive: Boolean = false,
    /** Deadzone + gamma + pressure-only EMA (the pressure preference trio). */
    val pressure: PressureShape = PressureShape.DEFAULT,
    /** Ink roughness as a 0–100 slider value (per-tool); 0 is off. */
    val grain: Double = 0.0,
    /** Stable grain seed; 0 means "no stroke data", under which grain is off anyway. */
    val grainSeed: Long = 0L,
    /** Brush scatter as a 0–100 slider value (per-tool); 0 is off. */
    val scatter: Double = 0.0,
    /** Stable scatter seed (see [StrokeGrain.scatterSeedOf]). */
    val scatterSeed: Long = 0L,
    /** Calligraphy nib lock in degrees, or [NibAngle.FOLLOW_TRAVEL]. */
    val nibAngleLockDeg: Double = NibAngle.FOLLOW_TRAVEL,
) {
    companion object {
        val DEFAULT = InkEngineOptions()
    }
}

/**
 * The render-time half of the ink-engine preferences, installed process-wide exactly like the
 * paper-aware ink transform ([com.xnota.core.model.InkRendering]): [com.xnota.core.model.Stroke]
 * resolves its geometry from deep inside both render stacks where no preference can be handed
 * down, and every one of these knobs is a property of the *reader* rather than of the stored
 * stroke — flip one and the ink re-renders, nothing on disk moves.
 *
 * The per-stroke half (grain, scatter) travels in the tool config and joins the options at
 * geometry-build time; [StrokeEngine] itself never reads this object, which keeps the engine pure
 * and its conformance vectors stable.
 */
object EngineTuning {
    @Volatile
    var filter: StrokeFilter = StrokeFilter.DEFAULT

    /** Off by default: historical ink stays byte-for-byte identical until the owner opts in
     *  (fast ink crisper, slow ink steadier once on). */
    @Volatile
    var adaptiveSmoothing: Boolean = false

    /** On by default: the predicted tail retracts at pen-up, so it costs nothing permanent. */
    @Volatile
    var prediction: Boolean = true

    @Volatile
    var pressure: PressureShape = PressureShape.DEFAULT

    @Volatile
    var nibAngleLockDeg: Double = NibAngle.FOLLOW_TRAVEL

    /**
     * Bumped by [install] so cached stroke geometry can tell a tuning change from a stroke edit:
     * a [com.xnota.core.model.Stroke] built under an older generation rebuilds its ribbon the
     * next time it is painted, exactly like an edit invalidates it, without every render path
     * having to carry the options down.
     */
    @Volatile
    var generation: Int = 0
        private set

    /** Install the reader-level tuning in one call; bumps [generation] when anything moved. */
    fun install(
        filter: StrokeFilter = this.filter,
        adaptiveSmoothing: Boolean = this.adaptiveSmoothing,
        prediction: Boolean = this.prediction,
        pressure: PressureShape = this.pressure,
        nibAngleLockDeg: Double = this.nibAngleLockDeg,
    ) {
        val changed = filter != this.filter || adaptiveSmoothing != this.adaptiveSmoothing ||
            prediction != this.prediction || pressure != this.pressure || nibAngleLockDeg != this.nibAngleLockDeg
        this.filter = filter
        this.adaptiveSmoothing = adaptiveSmoothing
        this.prediction = prediction
        this.pressure = pressure
        this.nibAngleLockDeg = nibAngleLockDeg
        if (changed) generation++
    }

    /** The options a stroke of the given per-stroke grain/scatter renders with. */
    fun optionsFor(grain: Double, grainSeed: Long, scatter: Double, scatterSeed: Long): InkEngineOptions =
        InkEngineOptions(
            filter = filter,
            adaptive = adaptiveSmoothing,
            pressure = pressure,
            grain = grain,
            grainSeed = grainSeed,
            scatter = scatter,
            scatterSeed = scatterSeed,
            nibAngleLockDeg = nibAngleLockDeg,
        )
}
