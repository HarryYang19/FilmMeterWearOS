package com.harryyang.filmmeter

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.PathInterpolator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Mechanical-inspired, touch-first round meter. Long-press a dial to enter
 * persistent edit mode, then circular drags adjust it. A second long press or
 * five seconds without interaction confirms and locks the selected parameter.
 */
class MeterDialView(
    context: Context,
    initialState: MeterState,
    private val onStateChanged: (MeterState) -> Unit
) : View(context) {
    private var state = initialState
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f
    private var settingsOpen = false
    private var settingsPage = 0

    private val handler = Handler(Looper.getMainLooper())
    private var pendingLongPress: Runnable? = null
    private var editTimeout: Runnable? = null
    private var pressParameter: ExposurePriority? = null
    private var downX = 0f
    private var downY = 0f
    private var longPressHandled = false
    private var gestureTracking = false
    private var editingParameter: ExposurePriority? = null
    private var lastFingerAngle = 0f
    private var angleAccumulator = 0f
    private var highlightPulse = 0f
    private var highlightAnimator: ValueAnimator? = null
    private var apertureRotation = 0f
    private var shutterRotation = 0f
    private var apertureAnimator: ValueAnimator? = null
    private var shutterAnimator: ValueAnimator? = null

    private val scenePresets = listOf(
        Triple("DARK ROOM", 4.0, "Very dim interior"),
        Triple("ROOM LIGHT", 6.0, "Typical indoor light"),
        Triple("BRIGHT ROOM", 8.0, "Bright indoor light"),
        Triple("OVERCAST", 12.0, "Open overcast daylight"),
        Triple("OPEN SHADE", 13.0, "Open shade outdoors"),
        Triple("SUNNY", 15.0, "Bright sunlight")
    )

    private data class MeterPalette(
        val background: Int,
        val dial: Int,
        val muted: Int,
        val bright: Int,
        val accent: Int,
        val panel: Int,
        val panelEdge: Int,
        val selectedFill: Int,
        val calculatedFill: Int,
        val editFill: Int,
        val buttonFill: Int,
        val isoFill: Int
    )

    private val palette: MeterPalette
        get() = paletteFor(state.colorScheme)

    private val backgroundColor: Int get() = palette.background
    private val dialColor: Int get() = palette.dial
    private val mutedColor: Int get() = palette.muted
    private val brightColor: Int get() = palette.bright
    private val accentColor: Int get() = palette.accent
    private val panelColor: Int get() = palette.panel
    private val panelEdgeColor: Int get() = palette.panelEdge

    private fun paletteFor(scheme: MeterColorScheme): MeterPalette = when (scheme) {
        MeterColorScheme.BRASS -> MeterPalette(
            Color.rgb(5, 6, 7), Color.rgb(82, 78, 68), Color.rgb(145, 141, 130),
            Color.rgb(238, 229, 207), Color.rgb(218, 174, 91), Color.rgb(20, 21, 21),
            Color.rgb(83, 72, 51), Color.rgb(40, 37, 30), Color.rgb(25, 29, 31),
            Color.rgb(77, 54, 25), Color.rgb(55, 45, 29), Color.rgb(12, 13, 13)
        )
        MeterColorScheme.ARCTIC -> MeterPalette(
            Color.rgb(4, 10, 16), Color.rgb(42, 72, 91), Color.rgb(133, 164, 181),
            Color.rgb(226, 242, 250), Color.rgb(86, 204, 231), Color.rgb(11, 23, 33),
            Color.rgb(43, 83, 105), Color.rgb(27, 49, 64), Color.rgb(13, 35, 48),
            Color.rgb(17, 57, 73), Color.rgb(21, 55, 72), Color.rgb(7, 17, 25)
        )
        MeterColorScheme.GARNET -> MeterPalette(
            Color.rgb(16, 5, 9), Color.rgb(87, 35, 47), Color.rgb(190, 135, 143),
            Color.rgb(250, 231, 231), Color.rgb(240, 91, 111), Color.rgb(35, 12, 19),
            Color.rgb(105, 47, 61), Color.rgb(58, 25, 34), Color.rgb(43, 17, 26),
            Color.rgb(79, 25, 39), Color.rgb(75, 27, 40), Color.rgb(22, 8, 13)
        )
        MeterColorScheme.FOREST -> MeterPalette(
            Color.rgb(5, 13, 8), Color.rgb(45, 76, 53), Color.rgb(143, 170, 145),
            Color.rgb(230, 244, 222), Color.rgb(169, 220, 105), Color.rgb(12, 27, 17),
            Color.rgb(57, 94, 62), Color.rgb(30, 51, 34), Color.rgb(17, 40, 23),
            Color.rgb(36, 67, 34), Color.rgb(35, 66, 38), Color.rgb(8, 19, 12)
        )
        MeterColorScheme.GRAPHITE -> MeterPalette(
            Color.rgb(8, 9, 11), Color.rgb(75, 80, 88), Color.rgb(157, 163, 172),
            Color.rgb(241, 244, 248), Color.rgb(190, 202, 218), Color.rgb(22, 24, 28),
            Color.rgb(86, 92, 103), Color.rgb(43, 47, 54), Color.rgb(30, 35, 42),
            Color.rgb(56, 61, 70), Color.rgb(49, 54, 63), Color.rgb(13, 15, 18)
        )
    }

    val isInSettings: Boolean get() = settingsOpen

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
    }
    private val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
    }
    private val calculatedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
    }
    private val arcRect = RectF()
    private val scratchRect = RectF()


    init {
        isFocusable = true
        isClickable = true
        calculateTargetRotations(animate = false)
    }

    fun updateState(newState: MeterState) {
        val old = state
        state = newState
        val oldExposure = old.exposure
        val newExposure = newState.exposure
        if (oldExposure.apertureIndex != newExposure.apertureIndex ||
            oldExposure.shutterIndex != newExposure.shutterIndex ||
            old.priority != newState.priority || old.lockedIndex != newState.lockedIndex
        ) {
            calculateTargetRotations(animate = true)
        }
        invalidate()
    }

    fun closeSettings() {
        settingsOpen = false
        settingsPage = 0
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        radius = min(w, h) * 0.495f

        // Wear OS uses a left-edge, left-to-right swipe as its primary dismiss
        // gesture. Claim only the left starting edge. This app already keeps
        // navigation bars in sticky-immersive mode, where Android's usual 200dp
        // vertical exclusion cap does not apply. Right-edge, bottom/home and
        // hardware navigation remain system-controlled.
        val density = resources.displayMetrics.density
        val exclusionWidth = (30f * density).roundToInt().coerceAtLeast(1).coerceAtMost(w)
        systemGestureExclusionRects = listOf(Rect(0, 0, exclusionWidth, h))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(backgroundColor)
        if (radius <= 0f) return
        val save = canvas.save()
        canvas.translate(cx, cy)
        fillPaint.color = backgroundColor
        canvas.drawCircle(0f, 0f, radius, fillPaint)
        if (settingsOpen) {
            when (settingsPage) {
                1 -> drawScenePicker(canvas)
                2 -> drawColorSchemePicker(canvas)
                else -> drawSettings(canvas)
            }
        } else {
            drawMeter(canvas)
        }
        canvas.restoreToCount(save)
    }

    private fun drawMeter(canvas: Canvas) {
        // Use almost the full round display. The outer index track now sits
        // close to the physical edge while retaining a small clipping margin.
        val outerR = radius * 0.945f
        val innerR = radius * 0.695f
        val outerLabelR = radius * 0.845f
        val innerLabelR = radius * 0.595f
        val apertureLocked = state.priority == ExposurePriority.APERTURE
        val shutterLocked = state.priority == ExposurePriority.SHUTTER
        val apertureEditing = editingParameter == ExposurePriority.APERTURE
        val shutterEditing = editingParameter == ExposurePriority.SHUTTER

        // Fine index marks and two tactile-looking knurled tracks.
        drawScaleTrack(canvas, outerR, radius * 0.011f, dialColor)
        drawScaleTrack(canvas, innerR, radius * 0.009f, dialColor)
        drawTicks(canvas, outerR, 96, radius * 0.023f, radius * 0.005f, mutedColor, apertureRotation)
        drawTicks(canvas, innerR, 72, radius * 0.020f, radius * 0.005f, mutedColor, shutterRotation)

        if (apertureLocked || apertureEditing) {
            drawActiveArc(canvas, outerR, apertureEditing, 300f)
        }
        if (shutterLocked || shutterEditing) {
            drawActiveArc(canvas, innerR, shutterEditing, 300f)
        }

        
        drawDialLabels(
            canvas = canvas,
            labels = ExposureMath.apertureLabels,
            labelRadius = outerLabelR,
            rotation = apertureRotation,
            fontSize = radius * 0.100f,
            selectedIndex = state.exposure.apertureIndex,
            isFixed = apertureLocked,
            isCalculated = !apertureLocked,
            isEditing = apertureEditing,
            hideRightGap = true
        )
        drawDialLabels(
            canvas = canvas,
            labels = ExposureMath.shutterLabels,
            labelRadius = innerLabelR,
            rotation = shutterRotation,
            fontSize = radius * 0.087f,
            selectedIndex = state.exposure.shutterIndex,
            isFixed = shutterLocked,
            isCalculated = !shutterLocked,
            isEditing = shutterEditing,
            hideRightGap = true
        )

        drawCentralHub(canvas)
        drawIsoPlate(canvas)
        drawInteractionHint(canvas)
    }

    private fun drawScaleTrack(canvas: Canvas, r: Float, width: Float, color: Int) {
        arcRect.set(-r, -r, r, r)
        linePaint.color = color
        linePaint.strokeWidth = width
        canvas.drawArc(arcRect, 21f, 318f, false, linePaint)
    }

    private fun drawActiveArc(canvas: Canvas, r: Float, editing: Boolean, sweep: Float) {
        arcRect.set(-r, -r, r, r)
        linePaint.color = accentColor
        linePaint.strokeWidth = radius * if (editing) 0.026f else 0.017f
        if (editing) {
            val glow = radius * (0.12f + highlightPulse * 0.10f)
            linePaint.setShadowLayer(glow, 0f, 0f, accentColor)
        } else {
            linePaint.setShadowLayer(radius * 0.035f, 0f, 0f, accentColor)
        }
        canvas.drawArc(arcRect, 21f, sweep, false, linePaint)
        linePaint.clearShadowLayer()
    }

    private fun drawTicks(
        canvas: Canvas,
        r: Float,
        count: Int,
        length: Float,
        width: Float,
        color: Int,
        rotation: Float
    ) {
        linePaint.color = color
        for (i in 0 until count) {
            val angleDeg = -90f + i * (360f / count) + rotation
            val normalized = normalizeAngle(angleDeg)
            if (normalized < 21f || normalized > 339f) continue
            val angle = Math.toRadians(angleDeg.toDouble())
            val major = i % (count / 12).coerceAtLeast(1) == 0
            val inner = r - if (major) length * 1.5f else length * 0.65f
            linePaint.strokeWidth = if (major) width * 1.75f else width
            canvas.drawLine(
                (cos(angle) * inner).toFloat(), (sin(angle) * inner).toFloat(),
                (cos(angle) * r).toFloat(), (sin(angle) * r).toFloat(),
                linePaint
            )
        }
    }

    private fun drawDialLabels(
        canvas: Canvas,
        labels: List<String>,
        labelRadius: Float,
        rotation: Float,
        fontSize: Float,
        selectedIndex: Int,
        isFixed: Boolean,
        isCalculated: Boolean,
        isEditing: Boolean,
        hideRightGap: Boolean
    ) {
        // Paint every neighbouring mark first, then draw the result value last.
        // That keeps the enlarged calculated value crisp even when its wider
        // highlight plate overlaps the adjacent scale labels by a few pixels.
        val drawOrder = labels.indices.filter { it != selectedIndex } + selectedIndex
        for (i in drawOrder) {
            val angleDeg = -90f + i * (360f / labels.size) + rotation
            val normalized = normalizeAngle(angleDeg)
            // The small opening at three o'clock is reserved for the EI plate.
            if (hideRightGap && (normalized < 22f || normalized > 338f)) continue
            val angle = Math.toRadians(angleDeg.toDouble())
            val x = (cos(angle) * labelRadius).toFloat()
            val y = (sin(angle) * labelRadius).toFloat()
            val selected = i == selectedIndex
            val emphasis = when {
                selected && isCalculated && labels.size <= 10 -> 2.10f
                selected && isCalculated -> 2.05f
                selected && isFixed -> 1.45f
                selected -> 1.45f
                else -> 1.0f
            }
            val textSize = fontSize * emphasis

            
            val labelColor = when {
                selected && isEditing -> accentColor
                selected && isCalculated -> Color.WHITE
                selected && isFixed -> brightColor
                selected -> brightColor
                else -> mutedColor
            }
            drawText(
                canvas,
                labels[i],
                x,
                y + textSize * 0.34f,
                textSize,
                labelColor,
                bold = selected,
                condensed = selected && isCalculated
            )
        }
    }

    private fun drawCentralHub(canvas: Canvas) {
        // A larger hub fills the centre more effectively, without colliding with
        // the inner shutter scale.
        val hubR = radius * 0.405f
        fillPaint.color = panelColor
        canvas.drawCircle(0f, 0f, hubR, fillPaint)
        linePaint.color = panelEdgeColor
        linePaint.strokeWidth = radius * 0.010f
        canvas.drawCircle(0f, 0f, hubR, linePaint)
        linePaint.color = dialColor
        linePaint.strokeWidth = radius * 0.004f
        canvas.drawCircle(0f, 0f, hubR * 0.94f, linePaint)

        val hasAutoReading = state.source == MeterSource.AUTO_SENSOR && state.lux != null
        val isWaiting = state.source == MeterSource.AUTO_SENSOR && state.lux == null
        val topLabel = when {
            hasAutoReading -> "INCIDENT METER"
            isWaiting -> "LIGHT SENSOR"
            else -> "MANUAL METER"
        }
        drawText(canvas, topLabel, 0f, -radius * 0.285f, radius * 0.043f, mutedColor)

        val mainReading = when {
            hasAutoReading -> formatLux(requireNotNull(state.lux))
            isWaiting -> "-- lx"
            else -> "EV ${String.format(Locale.US, "%.1f", state.manualEv100)}"
        }
        drawFittedText(canvas, mainReading, 0f, -radius * 0.168f, radius * 0.086f, radius * 0.70f, brightColor, bold = true)

        // Make both members of the calculated exposure pair the dominant centre
        // typography. Separate alignment gives the shutter enough room for 1/1000.
        val exposure = state.exposure
        val apertureText = "f/${ExposureMath.formatAperture(exposure.aperture)}"
        val shutterText = formatShutter(exposure.shutterSeconds)
        drawText(canvas, apertureText, -radius * 0.180f, radius * 0.048f, radius * 0.128f, Color.WHITE, bold = true)
        drawText(canvas, shutterText, radius * 0.190f, radius * 0.048f, radius * 0.125f, Color.WHITE, bold = true)
        linePaint.color = accentColor
        linePaint.strokeWidth = radius * 0.006f
        canvas.drawLine(0f, -radius * 0.050f, 0f, radius * 0.074f, linePaint)

        val evText = if (isWaiting) "WAITING FOR LIGHT" else "EV100  ${String.format(Locale.US, "%.1f", state.effectiveEv100)}"
        drawText(canvas, evText, 0f, radius * 0.176f, radius * 0.086f, mutedColor)

        val lockLabel = when (state.priority) {
            ExposurePriority.APERTURE -> "APERTURE LOCKED"
            ExposurePriority.SHUTTER -> "SHUTTER LOCKED"
            ExposurePriority.NONE -> "AUTO PAIR"
        }
        val errorLabel = String.format(Locale.US, "Δ%+.1f EV", exposure.errorStops)
        drawFittedText(
            canvas,
            "$lockLabel  ·  $errorLabel",
            0f,
            radius * 0.292f,
            radius * 0.033f,
            radius * 0.72f,
            if (editingParameter != null) accentColor else mutedColor
        )
    }

    private fun drawIsoPlate(canvas: Canvas) {
        val width = radius * 0.305f
        val height = radius * 0.185f
        val left = radius * 0.565f
        val top = -height * 0.52f
        scratchRect.set(left, top, left + width, top + height)
        fillPaint.color = palette.isoFill
        canvas.drawRoundRect(scratchRect, radius * 0.025f, radius * 0.025f, fillPaint)
        linePaint.color = accentColor
        linePaint.strokeWidth = radius * 0.008f
        canvas.drawRoundRect(scratchRect, radius * 0.025f, radius * 0.025f, linePaint)
        drawText(canvas, "EI / ISO", scratchRect.centerX(), top + height * 0.35f, radius * 0.040f, mutedColor)
        drawText(canvas, state.ei.toString(), scratchRect.centerX(), top + height * 0.79f, radius * 0.082f, brightColor, bold = true)
    }

    private fun drawInteractionHint(canvas: Canvas) {
        val label = if (editingParameter != null) {
            " "
        } else {
            " "
        }
        drawFittedText(canvas, label, 0f, radius * 0.955f, radius * 0.040f, radius * 1.80f, mutedColor)
    }

    private fun drawSettings(canvas: Canvas) {
        drawMenuFrame(canvas, "METER SETTINGS", "TOUCH THE LARGE CONTROLS")
        drawStepRow(canvas, "FILM SPEED", "EI ${state.ei}", -0.345f)
        drawStepRow(canvas, "CALIBRATION", String.format(Locale.US, "%+.1f EV", state.calibrationBiasEv), -0.055f)
        drawToggleRow(
            canvas,
            "MEASUREMENT",
            if (state.source == MeterSource.AUTO_SENSOR) "AUTO SENSOR" else "MANUAL EV",
            0.235f,
            state.source == MeterSource.AUTO_SENSOR
        )
        if (state.source == MeterSource.MANUAL_EV) {
            drawStepRow(canvas, "MANUAL EXPOSURE", String.format(Locale.US, "EV %.1f", state.manualEv100), 0.525f)
            drawFooterButton(canvas, "SCENES", -0.215f, 0.765f)
            drawFooterButton(canvas, "COLORS", 0.215f, 0.765f)
        } else {
            val sensorStatus = when {
                !state.sensorAvailable -> "NO SENSOR  ·  MANUAL AVAILABLE"
                state.sensorResponding -> "${state.sensorName.take(22)}  ·  LIVE"
                else -> "${state.sensorName.take(22)}  ·  WAITING"
            }
            drawInfoRow(canvas, "LIGHT SENSOR", sensorStatus, 0.525f)
            drawFooterButton(canvas, "COLORS", -0.215f, 0.765f)
            drawFooterButton(canvas, "DONE", 0.215f, 0.765f)
        }
    }

    private fun drawMenuFrame(canvas: Canvas, title: String, subtitle: String) {
        fillPaint.color = backgroundColor
        canvas.drawCircle(0f, 0f, radius, fillPaint)
        linePaint.color = dialColor
        linePaint.strokeWidth = radius * 0.010f
        canvas.drawCircle(0f, 0f, radius * 0.91f, linePaint)
        drawText(canvas, title, 0f, -radius * 0.705f, radius * 0.082f, brightColor, bold = true)
        drawFittedText(canvas, subtitle, 0f, -radius * 0.565f, radius * 0.053f, radius * 1.34f, mutedColor)
        fillPaint.color = palette.buttonFill
        canvas.drawCircle(radius * 0.67f, -radius * 0.60f, radius * 0.085f, fillPaint)
        linePaint.color = accentColor
        linePaint.strokeWidth = radius * 0.006f
        canvas.drawCircle(radius * 0.67f, -radius * 0.60f, radius * 0.085f, linePaint)
        drawText(canvas, "×", radius * 0.67f, -radius * 0.568f, radius * 0.090f, accentColor, bold = true)
    }

    private fun drawStepRow(canvas: Canvas, title: String, value: String, centerYFactor: Float) {
        val centerY = radius * centerYFactor
        val rect = RectF(-radius * 0.695f, centerY - radius * 0.142f, radius * 0.695f, centerY + radius * 0.142f)
        fillPaint.color = panelColor
        canvas.drawRoundRect(rect, radius * 0.060f, radius * 0.060f, fillPaint)
        linePaint.color = panelEdgeColor
        linePaint.strokeWidth = radius * 0.005f
        canvas.drawRoundRect(rect, radius * 0.060f, radius * 0.060f, linePaint)
        drawText(canvas, title, 0f, centerY - radius * 0.022f, radius * 0.062f, mutedColor)
        drawFittedText(canvas, value, 0f, centerY + radius * 0.083f, radius * 0.094f, radius * 0.78f, brightColor, bold = true)
        drawRoundButton(canvas, -radius * 0.505f, centerY, "−")
        drawRoundButton(canvas, radius * 0.505f, centerY, "+")
    }

    private fun drawToggleRow(canvas: Canvas, title: String, value: String, centerYFactor: Float, active: Boolean) {
        val centerY = radius * centerYFactor
        val rect = RectF(-radius * 0.695f, centerY - radius * 0.142f, radius * 0.695f, centerY + radius * 0.142f)
        fillPaint.color = if (active) palette.selectedFill else panelColor
        canvas.drawRoundRect(rect, radius * 0.060f, radius * 0.060f, fillPaint)
        linePaint.color = if (active) accentColor else panelEdgeColor
        linePaint.strokeWidth = radius * 0.006f
        canvas.drawRoundRect(rect, radius * 0.060f, radius * 0.060f, linePaint)
        drawText(canvas, title, 0f, centerY - radius * 0.022f, radius * 0.062f, mutedColor)
        drawFittedText(canvas, value, 0f, centerY + radius * 0.083f, radius * 0.094f, radius * 1.03f, if (active) accentColor else brightColor, bold = true)
    }

    private fun drawInfoRow(canvas: Canvas, title: String, value: String, centerYFactor: Float) {
        val centerY = radius * centerYFactor
        val rect = RectF(-radius * 0.695f, centerY - radius * 0.142f, radius * 0.695f, centerY + radius * 0.142f)
        fillPaint.color = panelColor
        canvas.drawRoundRect(rect, radius * 0.060f, radius * 0.060f, fillPaint)
        linePaint.color = panelEdgeColor
        linePaint.strokeWidth = radius * 0.005f
        canvas.drawRoundRect(rect, radius * 0.060f, radius * 0.060f, linePaint)
        drawText(canvas, title, 0f, centerY - radius * 0.022f, radius * 0.062f, mutedColor)
        drawFittedText(canvas, value, 0f, centerY + radius * 0.083f, radius * 0.066f, radius * 1.18f, brightColor, bold = true)
    }

    private fun drawRoundButton(canvas: Canvas, x: Float, y: Float, label: String) {
        fillPaint.color = palette.buttonFill
        canvas.drawCircle(x, y, radius * 0.096f, fillPaint)
        linePaint.color = accentColor
        linePaint.strokeWidth = radius * 0.007f
        canvas.drawCircle(x, y, radius * 0.096f, linePaint)
        drawText(canvas, label, x, y + radius * 0.041f, radius * 0.132f, brightColor, bold = true)
    }

    private fun drawFooterButton(canvas: Canvas, label: String, centerXFactor: Float, centerYFactor: Float) {
        val x = radius * centerXFactor
        val y = radius * centerYFactor
        val rect = RectF(x - radius * 0.190f, y - radius * 0.078f, x + radius * 0.190f, y + radius * 0.078f)
        fillPaint.color = palette.buttonFill
        canvas.drawRoundRect(rect, radius * 0.050f, radius * 0.050f, fillPaint)
        linePaint.color = accentColor
        linePaint.strokeWidth = radius * 0.006f
        canvas.drawRoundRect(rect, radius * 0.050f, radius * 0.050f, linePaint)
        drawText(canvas, label, x, y + radius * 0.028f, radius * 0.064f, brightColor, bold = true)
    }

    private fun drawBottomButton(canvas: Canvas, label: String, centerYFactor: Float) {
        val y = radius * centerYFactor
        val rect = RectF(-radius * 0.55f, y - radius * 0.082f, radius * 0.55f, y + radius * 0.082f)
        fillPaint.color = palette.buttonFill
        canvas.drawRoundRect(rect, radius * 0.05f, radius * 0.05f, fillPaint)
        linePaint.color = accentColor
        linePaint.strokeWidth = radius * 0.006f
        canvas.drawRoundRect(rect, radius * 0.05f, radius * 0.05f, linePaint)
        drawFittedText(canvas, label, 0f, y + radius * 0.027f, radius * 0.067f, radius * 0.98f, brightColor, bold = true)
    }

    private fun drawScenePicker(canvas: Canvas) {
        drawMenuFrame(canvas, "SCENE PRESETS", "SELECT A STARTING EXPOSURE")
        val columnX = listOf(-radius * 0.36f, radius * 0.36f)
        val rowY = listOf(-radius * 0.35f, -radius * 0.02f, radius * 0.31f)
        scenePresets.forEachIndexed { index, scene ->
            val x = columnX[index % 2]
            val y = rowY[index / 2]
            val rect = RectF(x - radius * 0.300f, y - radius * 0.125f, x + radius * 0.300f, y + radius * 0.125f)
            val selected = state.source == MeterSource.MANUAL_EV && abs(state.manualEv100 - scene.second) < 0.01
            fillPaint.color = if (selected) palette.selectedFill else panelColor
            canvas.drawRoundRect(rect, radius * 0.035f, radius * 0.035f, fillPaint)
            linePaint.color = if (selected) accentColor else panelEdgeColor
            linePaint.strokeWidth = radius * 0.005f
            canvas.drawRoundRect(rect, radius * 0.035f, radius * 0.035f, linePaint)
            drawFittedText(canvas, scene.first, x, y + radius * 0.018f, radius * 0.068f, radius * 0.54f, if (selected) accentColor else brightColor, bold = true)
            drawText(canvas, "EV ${String.format(Locale.US, "%.1f", scene.second)}", x, y + radius * 0.090f, radius * 0.052f, mutedColor)
        }
        drawBottomButton(canvas, "BACK TO SETTINGS", 0.765f)
    }

    private fun drawColorSchemePicker(canvas: Canvas) {
        drawMenuFrame(canvas, "COLOR SCHEME", "FIVE DARKROOM-INSPIRED PALETTES")
        val schemes = MeterColorScheme.values()
        val rowCenters = listOf(-0.39f, -0.195f, 0f, 0.195f, 0.39f)
        schemes.forEachIndexed { index, scheme ->
            val y = radius * rowCenters[index]
            val colors = paletteFor(scheme)
            val rect = RectF(-radius * 0.70f, y - radius * 0.078f, radius * 0.70f, y + radius * 0.078f)
            val selected = state.colorScheme == scheme
            fillPaint.color = if (selected) colors.selectedFill else colors.panel
            canvas.drawRoundRect(rect, radius * 0.042f, radius * 0.042f, fillPaint)
            linePaint.color = if (selected) colors.accent else colors.panelEdge
            linePaint.strokeWidth = radius * if (selected) 0.008f else 0.004f
            canvas.drawRoundRect(rect, radius * 0.042f, radius * 0.042f, linePaint)
            fillPaint.color = colors.accent
            canvas.drawCircle(-radius * 0.54f, y, radius * 0.045f, fillPaint)
            linePaint.color = colors.bright
            linePaint.strokeWidth = radius * 0.005f
            canvas.drawCircle(-radius * 0.54f, y, radius * 0.045f, linePaint)
            drawText(canvas, scheme.title.uppercase(Locale.US), -radius * 0.12f, y + radius * 0.020f, radius * 0.071f, colors.bright, bold = selected)
            if (selected) {
                drawText(canvas, "✓", radius * 0.53f, y + radius * 0.024f, radius * 0.080f, colors.accent, bold = true)
            }
        }
        drawBottomButton(canvas, "BACK TO SETTINGS", 0.765f)
    }

    private fun drawText(
        canvas: Canvas,
        text: String,
        x: Float,
        baselineY: Float,
        size: Float,
        color: Int,
        bold: Boolean = false,
        condensed: Boolean = false
    ) {
        val p = when {
            condensed -> calculatedPaint
            bold -> boldPaint
            else -> textPaint
        }
        p.textSize = size
        p.color = color
        p.clearShadowLayer()

        // 计算结果使用柔和光晕，不再使用方框
        if (condensed) {
            p.setShadowLayer(
                size * 0.03f,
                0f,
                0f,
                color
            )
        }
        
        canvas.drawText(text, x, baselineY, p)
        p.clearShadowLayer()
    }

    private fun textWidth(text: String, size: Float, bold: Boolean, condensed: Boolean = false): Float {
        val p = when {
            condensed -> calculatedPaint
            bold -> boldPaint
            else -> textPaint
        }
        p.textSize = size
        return p.measureText(text)
    }

    private fun drawFittedText(
        canvas: Canvas,
        text: String,
        x: Float,
        baselineY: Float,
        preferredSize: Float,
        maxWidth: Float,
        color: Int,
        bold: Boolean = false
    ) {
        val measured = textWidth(text, preferredSize, bold)
        val actualSize = if (measured > maxWidth && measured > 0f) {
            preferredSize * (maxWidth / measured).coerceAtLeast(0.72f)
        } else {
            preferredSize
        }
        drawText(canvas, text, x, baselineY, actualSize, color, bold)
    }

    private fun formatLux(lux: Double): String = when {
        lux >= 10_000 -> String.format(Locale.US, "%.1fk lx", lux / 1000.0)
        lux >= 1000 -> String.format(Locale.US, "%.2fk lx", lux / 1000.0)
        lux >= 100 -> String.format(Locale.US, "%.0f lx", lux)
        lux >= 10 -> String.format(Locale.US, "%.1f lx", lux)
        else -> String.format(Locale.US, "%.2f lx", lux)
    }

    private fun formatShutter(seconds: Double): String = when {
        seconds < 1.0 -> "1/${(1.0 / seconds).roundToInt()}"
        else -> "${seconds.toInt()}s"
    }

    private fun calculateTargetRotations(animate: Boolean) {
        val exposure = state.exposure
        val targetAperture = -exposure.apertureIndex * (360f / ExposureMath.apertureValues.size)
        val targetShutter = -exposure.shutterIndex * (360f / ExposureMath.shutterSeconds.size)
        apertureRotation = animateRotation(apertureRotation, targetAperture, animate, true)
        shutterRotation = animateRotation(shutterRotation, targetShutter, animate, false)
    }

    private fun animateRotation(current: Float, target: Float, animate: Boolean, aperture: Boolean): Float {
        val delta = ((target - current + 540f) % 360f) - 180f
        val finalValue = current + delta
        val animator = ValueAnimator.ofFloat(current, finalValue).apply {
            duration = when {
                !animate -> 0L
                editingParameter == ExposurePriority.APERTURE && aperture -> 42L
                editingParameter == ExposurePriority.SHUTTER && !aperture -> 42L
                editingParameter != null -> 72L
                else -> 460L
            }
            interpolator = PathInterpolator(0.18f, 0.92f, 0.24f, 1.0f)
            addUpdateListener {
                val value = it.animatedValue as Float
                if (aperture) apertureRotation = value else shutterRotation = value
                invalidate()
            }
        }
        if (aperture) {
            apertureAnimator?.cancel()
            apertureAnimator = animator
        } else {
            shutterAnimator?.cancel()
            shutterAnimator = animator
        }
        animator.start()
        return if (animate) current else finalValue
    }

    private fun startHighlightPulse() {
        highlightAnimator?.cancel()
        highlightAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 760L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
            addUpdateListener {
                highlightPulse = it.animatedValue as Float
                invalidate()
            }
        }
        highlightAnimator?.start()
    }

    private fun stopHighlightPulse() {
        highlightAnimator?.cancel()
        highlightAnimator = null
        highlightPulse = 0f
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (settingsOpen) return true
                // Keep ancestors from stealing a dial drag mid-gesture.
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = event.x
                downY = event.y
                longPressHandled = false
                gestureTracking = false
                val dx = event.x - cx
                val dy = event.y - cy
                pressParameter = if (isEiPlateHit(dx, dy)) null else dialAt(dx, dy)
                val parameter = pressParameter
                if (parameter != null) {
                    val task = Runnable {
                        pendingLongPress = null
                        longPressHandled = true
                        if (editingParameter == parameter) {
                            // A second long press explicitly confirms the current setting.
                            finishDialEdit()
                        } else {
                            beginDialEdit(parameter)
                        }
                    }
                    pendingLongPress = task
                    handler.postDelayed(task, ViewConfiguration.getLongPressTimeout().toLong())
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (settingsOpen) return true
                val moved = hypot(event.x - downX, event.y - downY)
                val moveThreshold = if (editingParameter != null) {
                    (2.5f * resources.displayMetrics.density).coerceAtLeast(2f)
                } else {
                    ViewConfiguration.get(context).scaledTouchSlop.toFloat()
                }
                if (!longPressHandled && pendingLongPress != null && moved > moveThreshold) {
                    cancelPendingLongPress()
                    // During an already-open edit session, a normal circular drag adjusts the dial.
                    if (editingParameter != null && pressParameter == editingParameter) {
                        gestureTracking = true
                        lastFingerAngle = angleDegrees(downX - cx, downY - cy)
                        angleAccumulator = 0f
                        updateEditTimeout()
                        processMotionSamples(event)
                    }
                } else if (longPressHandled && editingParameter != null && gestureTracking) {
                    processMotionSamples(event)
                } else if (gestureTracking && editingParameter != null) {
                    processMotionSamples(event)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                cancelPendingLongPress()
                if (settingsOpen) {
                    handleSettingsTouch(event.x - cx, event.y - cy)
                    performClick()
                    return true
                }
                if (!longPressHandled && pressParameter == null) {
                    val dx = event.x - cx
                    val dy = event.y - cy
                    if (hypot(dx, dy) < radius * 0.435f || isEiPlateHit(dx, dy)) {
                        openSettings()
                    }
                } else if (editingParameter != null && pressParameter != null) {
                    // Lifting the finger ends a drag, not the edit session.
                    updateEditTimeout()
                }
                gestureTracking = false
                pressParameter = null
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelPendingLongPress()
                gestureTracking = false
                pressParameter = null
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /**
     * The visible rings have wide invisible hit bands. During an active edit,
     * the whole annular dial area maps to the already-selected parameter. This
     * is safe because the lock prevents accidental switching to the other dial.
     */
    private fun dialAt(dx: Float, dy: Float): ExposurePriority? {
        val distance = hypot(dx, dy) / radius
        editingParameter?.let { active ->
            if (distance in 0.405f..1.10f) return active
        }

        val apertureCenter = 0.88f
        val shutterCenter = 0.595f
        val apertureHit = distance in 0.72f..1.10f
        val shutterHit = distance in 0.405f..0.81f
        return when {
            apertureHit && shutterHit -> {
                if (abs(distance - apertureCenter) <= abs(distance - shutterCenter)) {
                    ExposurePriority.APERTURE
                } else {
                    ExposurePriority.SHUTTER
                }
            }
            apertureHit -> ExposurePriority.APERTURE
            shutterHit -> ExposurePriority.SHUTTER
            else -> null
        }
    }

    private fun isEiPlateHit(dx: Float, dy: Float): Boolean =
        dx in (radius * 0.53f)..(radius * 0.94f) && abs(dy) < radius * 0.145f

    private fun beginDialEdit(parameter: ExposurePriority) {
        val selectedIndex = when (parameter) {
            ExposurePriority.APERTURE -> state.exposure.apertureIndex
            ExposurePriority.SHUTTER -> state.exposure.shutterIndex
            ExposurePriority.NONE -> 0
        }
        editingParameter = parameter
        gestureTracking = true
        angleAccumulator = 0f
        lastFingerAngle = angleDegrees(downX - cx, downY - cy)
        val count = if (parameter == ExposurePriority.APERTURE) ExposureMath.apertureValues.size else ExposureMath.shutterSeconds.size
        state = state.copy(priority = parameter, lockedIndex = selectedIndex.coerceIn(0, count - 1))
        calculateTargetRotations(animate = true)
        startHighlightPulse()
        updateEditTimeout()
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onStateChanged(state)
        invalidate()
    }

    private fun processMotionSamples(event: MotionEvent) {
        // Consume historical samples before the current point to avoid skipping
        // detents when Android batches touch updates on the watch.
        for (historyIndex in 0 until event.historySize) {
            updateDialFromMotion(
                event.getHistoricalX(0, historyIndex) - cx,
                event.getHistoricalY(0, historyIndex) - cy
            )
        }
        updateDialFromMotion(event.x - cx, event.y - cy)
    }

    private fun updateDialFromMotion(dx: Float, dy: Float) {
        val parameter = editingParameter ?: return
        val currentAngle = angleDegrees(dx, dy)
        var delta = currentAngle - lastFingerAngle
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        lastFingerAngle = currentAngle
        angleAccumulator += delta
        if (abs(delta) > 0.25f) updateEditTimeout()

        val count = if (parameter == ExposurePriority.APERTURE) ExposureMath.apertureValues.size else ExposureMath.shutterSeconds.size
        // One detent is triggered after roughly 30% of the old threshold
        // (60% detent span with half-detent rounding), reducing rotation effort.
        val stepAngle = (360f / count) * 0.60f
        val rawSteps = (angleAccumulator / stepAngle).roundToInt()
        if (rawSteps == 0) return
        // Make the printed scale follow the direction of the finger, like a real dial.
        val steps = -rawSteps
        val nextIndex = (state.lockedIndex + steps).coerceIn(0, count - 1)
        if (nextIndex != state.lockedIndex) {
            state = state.copy(lockedIndex = nextIndex)
            angleAccumulator -= rawSteps * stepAngle
            calculateTargetRotations(animate = true)
            onStateChanged(state)
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            invalidate()
        } else {
            angleAccumulator = 0f
        }
    }

    private fun updateEditTimeout() {
        editTimeout?.let(handler::removeCallbacks)
        if (editingParameter == null) return
        val timeout = Runnable { finishDialEdit() }
        editTimeout = timeout
        handler.postDelayed(timeout, 5_000L)
    }

    private fun finishDialEdit() {
        editTimeout?.let(handler::removeCallbacks)
        editTimeout = null
        if (editingParameter != null) {
            editingParameter = null
            gestureTracking = false
            stopHighlightPulse()
            onStateChanged(state)
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        longPressHandled = true
        pressParameter = null
    }

    private fun cancelPendingLongPress() {
        pendingLongPress?.let { handler.removeCallbacks(it) }
        pendingLongPress = null
    }

    private fun angleDegrees(dx: Float, dy: Float): Float = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

    private fun normalizeAngle(value: Float): Float = ((value % 360f) + 360f) % 360f

    private fun openSettings() {
        if (editingParameter != null) finishDialEdit()
        settingsOpen = true
        settingsPage = 0
        invalidate()
    }

    private fun handleSettingsTouch(dx: Float, dy: Float) {
        val x = dx / radius
        val y = dy / radius
        if (hypot(x - 0.67f, y + 0.60f) < 0.13f) {
            closeSettings()
            return
        }

        if (settingsPage == 1) {
            if (y > 0.65f && abs(x) < 0.55f) {
                settingsPage = 0
                invalidate()
                return
            }
            val column = if (x < 0f) 0 else 1
            val row = when {
                y in -0.50f..-0.18f -> 0
                y in -0.17f..0.15f -> 1
                y in 0.16f..0.48f -> 2
                else -> -1
            }
            val index = if (row >= 0) row * 2 + column else -1
            if (index in scenePresets.indices) {
                val scene = scenePresets[index]
                state = state.copy(source = MeterSource.MANUAL_EV, manualEv100 = scene.second)
                stateChanged()
                settingsPage = 0
            }
            invalidate()
            return
        }

        if (settingsPage == 2) {
            if (y > 0.65f && abs(x) < 0.55f) {
                settingsPage = 0
            } else if (y in -0.49f..0.49f) {
                val index = ((y + 0.49f) / 0.195f).toInt().coerceIn(MeterColorScheme.values().indices)
                state = state.copy(colorScheme = MeterColorScheme.values()[index])
                stateChanged()
                settingsPage = 0
            }
            invalidate()
            return
        }

        val stepDirection = when {
            x in -0.67f..-0.35f -> -1
            x in 0.35f..0.67f -> 1
            else -> 0
        }
        when {
            y in -0.495f..-0.205f && stepDirection != 0 -> adjustEi(stepDirection)
            y in -0.205f..0.085f && stepDirection != 0 -> adjustCalibration(stepDirection)
            y in 0.095f..0.375f -> toggleSource()
            y in 0.385f..0.665f && state.source == MeterSource.MANUAL_EV && stepDirection != 0 -> adjustManualEv(stepDirection)
            y > 0.675f -> {
                when {
                    state.source == MeterSource.MANUAL_EV && x < 0f -> settingsPage = 1
                    state.source == MeterSource.MANUAL_EV && x >= 0f -> settingsPage = 2
                    state.source == MeterSource.AUTO_SENSOR && x < 0f -> settingsPage = 2
                    else -> closeSettings()
                }
                invalidate()
            }
        }
    }

    private fun adjustEi(direction: Int) {
        if (direction == 0) return
        val index = ExposureMath.eiValues.indexOf(state.ei).coerceAtLeast(0)
        val next = (index + direction).coerceIn(ExposureMath.eiValues.indices)
        state = state.copy(ei = ExposureMath.eiValues[next])
        stateChanged()
    }

    private fun adjustCalibration(direction: Int) {
        if (direction == 0) return
        state = state.copy(calibrationBiasEv = (state.calibrationBiasEv + direction * 0.1).coerceIn(-4.0, 4.0))
        stateChanged()
    }

    private fun adjustManualEv(direction: Int) {
        if (direction == 0) return
        state = state.copy(manualEv100 = (state.manualEv100 + direction * 0.3).coerceIn(-2.0, 20.0), source = MeterSource.MANUAL_EV)
        stateChanged()
    }

    private fun toggleSource() {
        val newSource = if (state.source == MeterSource.AUTO_SENSOR) MeterSource.MANUAL_EV else MeterSource.AUTO_SENSOR
        state = state.copy(source = newSource)
        stateChanged()
    }

    private fun stateChanged() {
        calculateTargetRotations(animate = true)
        onStateChanged(state)
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        cancelPendingLongPress()
        editTimeout?.let(handler::removeCallbacks)
        editTimeout = null
        apertureAnimator?.cancel()
        shutterAnimator?.cancel()
        stopHighlightPulse()
        super.onDetachedFromWindow()
    }
}
