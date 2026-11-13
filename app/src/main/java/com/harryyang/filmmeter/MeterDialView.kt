package com.harryyang.filmmeter

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
    private val arcRect = RectF()
    private val scratchRect = RectF()

    private val backgroundColor = Color.rgb(5, 6, 7)
    private val dialColor = Color.rgb(82, 78, 68)
    private val mutedColor = Color.rgb(145, 141, 130)
    private val brightColor = Color.rgb(238, 229, 207)
    private val accentColor = Color.rgb(218, 174, 91)
    private val panelColor = Color.rgb(20, 21, 21)
    private val panelEdgeColor = Color.rgb(83, 72, 51)

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
        radius = min(w, h) * 0.485f
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
            if (settingsPage == 0) drawSettings(canvas) else drawScenePicker(canvas)
        } else {
            drawMeter(canvas)
        }
        canvas.restoreToCount(save)
    }

    private fun drawMeter(canvas: Canvas) {
        val outerR = radius * 0.895f
        val innerR = radius * 0.690f
        val outerLabelR = radius * 0.795f
        val innerLabelR = radius * 0.595f
        val apertureLocked = state.priority == ExposurePriority.APERTURE
        val shutterLocked = state.priority == ExposurePriority.SHUTTER
        val apertureEditing = editingParameter == ExposurePriority.APERTURE
        val shutterEditing = editingParameter == ExposurePriority.SHUTTER

        // Fine index marks and two tactile-looking knurled tracks.
        drawScaleTrack(canvas, outerR, radius * 0.011f, dialColor)
        drawScaleTrack(canvas, innerR, radius * 0.009f, dialColor)
        drawTicks(canvas, outerR, 72, radius * 0.030f, radius * 0.006f, mutedColor, apertureRotation)
        drawTicks(canvas, innerR, 96, radius * 0.025f, radius * 0.005f, mutedColor, shutterRotation)

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
            fontSize = radius * 0.089f,
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
            fontSize = radius * 0.076f,
            selectedIndex = state.exposure.shutterIndex,
            isFixed = shutterLocked,
            isCalculated = !shutterLocked,
            isEditing = shutterEditing,
            hideRightGap = true
        )

        // The fixed index marker is shared by both concentric scales.
        linePaint.color = accentColor
        linePaint.strokeWidth = radius * 0.012f
        canvas.drawLine(0f, -radius * 0.985f, 0f, -radius * 0.905f, linePaint)
        val pointer = Path().apply {
            moveTo(-radius * 0.030f, -radius * 0.905f)
            lineTo(radius * 0.030f, -radius * 0.905f)
            lineTo(0f, -radius * 0.866f)
            close()
        }
        fillPaint.color = accentColor
        canvas.drawPath(pointer, fillPaint)

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
        for (i in labels.indices) {
            val angleDeg = -90f + i * (360f / labels.size) + rotation
            val normalized = normalizeAngle(angleDeg)
            // The small opening at three o'clock is reserved for the EI plate.
            if (hideRightGap && (normalized < 22f || normalized > 338f)) continue
            val angle = Math.toRadians(angleDeg.toDouble())
            val x = (cos(angle) * labelRadius).toFloat()
            val y = (sin(angle) * labelRadius).toFloat()
            val selected = i == selectedIndex
            val emphasis = when {
                selected && isCalculated -> 1.29f
                selected && isFixed -> 1.17f
                selected -> 1.24f
                else -> 1.0f
            }
            val textSize = fontSize * emphasis

            if (selected) {
                val width = textWidth(labels[i], textSize, bold = true) * 0.5f + radius * 0.040f
                val height = textSize * 0.84f
                scratchRect.set(x - width, y - height * 0.60f, x + width, y + height * 0.58f)
                fillPaint.color = when {
                    isEditing -> Color.rgb(77, 54, 25)
                    isCalculated -> Color.rgb(46, 39, 27)
                    isFixed -> Color.rgb(40, 37, 30)
                    else -> panelColor
                }
                if (isEditing) fillPaint.setShadowLayer(radius * (0.055f + highlightPulse * 0.075f), 0f, 0f, accentColor)
                canvas.drawRoundRect(scratchRect, radius * 0.034f, radius * 0.034f, fillPaint)
                fillPaint.clearShadowLayer()
                linePaint.color = if (isEditing || isCalculated) accentColor else panelEdgeColor
                linePaint.strokeWidth = radius * if (isEditing) 0.010f else 0.006f
                canvas.drawRoundRect(scratchRect, radius * 0.034f, radius * 0.034f, linePaint)
            }

            val labelColor = when {
                selected && isEditing -> accentColor
                selected && isCalculated -> accentColor
                selected && isFixed -> brightColor
                selected -> brightColor
                else -> Color.rgb(180, 176, 165)
            }
            drawText(
                canvas,
                labels[i],
                x,
                y + textSize * 0.34f,
                textSize,
                labelColor,
                bold = selected
            )
        }
    }

    private fun drawCentralHub(canvas: Canvas) {
        val hubR = radius * 0.360f
        fillPaint.color = panelColor
        canvas.drawCircle(0f, 0f, hubR, fillPaint)
        linePaint.color = panelEdgeColor
        linePaint.strokeWidth = radius * 0.010f
        canvas.drawCircle(0f, 0f, hubR, linePaint)
        linePaint.color = dialColor
        linePaint.strokeWidth = radius * 0.004f
        canvas.drawCircle(0f, 0f, hubR * 0.92f, linePaint)

        val hasAutoReading = state.source == MeterSource.AUTO_SENSOR && state.lux != null
        val isWaiting = state.source == MeterSource.AUTO_SENSOR && state.lux == null
        val topLabel = when {
            hasAutoReading -> "INCIDENT METER"
            isWaiting -> "LIGHT SENSOR"
            else -> "MANUAL METER"
        }
        drawText(canvas, topLabel, 0f, -radius * 0.190f, radius * 0.043f, mutedColor)

        val mainReading = when {
            hasAutoReading -> formatLux(requireNotNull(state.lux))
            isWaiting -> "-- lx"
            else -> "EV ${String.format(Locale.US, "%.1f", state.manualEv100)}"
        }
        drawText(canvas, mainReading, 0f, -radius * 0.055f, radius * 0.100f, brightColor, bold = true)

        // The exposure pair now occupies the former EV line; EV100 moves below it.
        val exposure = state.exposure
        val combination = "f/${ExposureMath.formatAperture(exposure.aperture)}  ${formatShutter(exposure.shutterSeconds)}"
        drawFittedText(canvas, combination, 0f, radius * 0.085f, radius * 0.078f, radius * 0.65f, accentColor, bold = true)

        val evText = if (isWaiting) "WAITING FOR LIGHT" else "EV100  ${String.format(Locale.US, "%.1f", state.effectiveEv100)}"
        drawText(canvas, evText, 0f, radius * 0.205f, radius * 0.043f, mutedColor)

        val lockLabel = when (state.priority) {
            ExposurePriority.APERTURE -> "APERTURE LOCKED"
            ExposurePriority.SHUTTER -> "SHUTTER LOCKED"
            ExposurePriority.NONE -> "AUTO PAIR"
        }
        val errorLabel = String.format(Locale.US, "Δ%+.1f EV", exposure.errorStops)
        drawText(
            canvas,
            "$lockLabel  ·  $errorLabel",
            0f,
            radius * 0.292f,
            radius * 0.032f,
            if (editingParameter != null) accentColor else mutedColor
        )
    }

    private fun drawIsoPlate(canvas: Canvas) {
        val width = radius * 0.305f
        val height = radius * 0.185f
        val left = radius * 0.565f
        val top = -height * 0.52f
        scratchRect.set(left, top, left + width, top + height)
        fillPaint.color = Color.rgb(12, 13, 13)
        canvas.drawRoundRect(scratchRect, radius * 0.025f, radius * 0.025f, fillPaint)
        linePaint.color = accentColor
        linePaint.strokeWidth = radius * 0.008f
        canvas.drawRoundRect(scratchRect, radius * 0.025f, radius * 0.025f, linePaint)
        drawText(canvas, "EI / ISO", scratchRect.centerX(), top + height * 0.35f, radius * 0.040f, mutedColor)
        drawText(canvas, state.ei.toString(), scratchRect.centerX(), top + height * 0.79f, radius * 0.082f, brightColor, bold = true)
    }

    private fun drawInteractionHint(canvas: Canvas) {
        val label = if (editingParameter != null) {
            "DRAG TO ADJUST  ·  HOLD AGAIN TO LOCK  ·  AUTO 5S"
        } else {
            "HOLD A DIAL TO SELECT  ·  TAP CENTER FOR MENU"
        }
        drawFittedText(canvas, label, 0f, radius * 0.972f, radius * 0.043f, radius * 1.72f, mutedColor)
    }

    private fun drawSettings(canvas: Canvas) {
        drawMenuFrame(canvas, "METER SETTINGS", "TOUCH THE LARGE − / + CONTROLS")
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
            drawBottomButton(canvas, "SCENE PRESETS", 0.785f)
        } else {
            val sensorStatus = when {
                !state.sensorAvailable -> "NO SENSOR  ·  MANUAL AVAILABLE"
                state.sensorResponding -> "${state.sensorName.take(22)}  ·  LIVE"
                else -> "${state.sensorName.take(22)}  ·  WAITING"
            }
            drawInfoRow(canvas, "LIGHT SENSOR", sensorStatus, 0.525f)
            drawBottomButton(canvas, "DONE", 0.785f)
        }
    }

    private fun drawMenuFrame(canvas: Canvas, title: String, subtitle: String) {
        fillPaint.color = backgroundColor
        canvas.drawCircle(0f, 0f, radius, fillPaint)
        linePaint.color = dialColor
        linePaint.strokeWidth = radius * 0.010f
        canvas.drawCircle(0f, 0f, radius * 0.91f, linePaint)
        drawText(canvas, title, 0f, -radius * 0.705f, radius * 0.068f, brightColor, bold = true)
        drawFittedText(canvas, subtitle, 0f, -radius * 0.565f, radius * 0.043f, radius * 1.28f, mutedColor)
        fillPaint.color = Color.rgb(48, 39, 25)
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
        drawText(canvas, title, 0f, centerY - radius * 0.022f, radius * 0.048f, mutedColor)
        drawFittedText(canvas, value, 0f, centerY + radius * 0.083f, radius * 0.079f, radius * 0.78f, brightColor, bold = true)
        drawRoundButton(canvas, -radius * 0.505f, centerY, "−")
        drawRoundButton(canvas, radius * 0.505f, centerY, "+")
    }

    private fun drawToggleRow(canvas: Canvas, title: String, value: String, centerYFactor: Float, active: Boolean) {
        val centerY = radius * centerYFactor
        val rect = RectF(-radius * 0.695f, centerY - radius * 0.142f, radius * 0.695f, centerY + radius * 0.142f)
        fillPaint.color = if (active) Color.rgb(38, 34, 25) else panelColor
        canvas.drawRoundRect(rect, radius * 0.060f, radius * 0.060f, fillPaint)
        linePaint.color = if (active) accentColor else panelEdgeColor
        linePaint.strokeWidth = radius * 0.006f
        canvas.drawRoundRect(rect, radius * 0.060f, radius * 0.060f, linePaint)
        drawText(canvas, title, 0f, centerY - radius * 0.022f, radius * 0.048f, mutedColor)
        drawFittedText(canvas, value, 0f, centerY + radius * 0.083f, radius * 0.079f, radius * 1.03f, if (active) accentColor else brightColor, bold = true)
    }

    private fun drawInfoRow(canvas: Canvas, title: String, value: String, centerYFactor: Float) {
        val centerY = radius * centerYFactor
        val rect = RectF(-radius * 0.695f, centerY - radius * 0.142f, radius * 0.695f, centerY + radius * 0.142f)
        fillPaint.color = panelColor
        canvas.drawRoundRect(rect, radius * 0.060f, radius * 0.060f, fillPaint)
        linePaint.color = panelEdgeColor
        linePaint.strokeWidth = radius * 0.005f
        canvas.drawRoundRect(rect, radius * 0.060f, radius * 0.060f, linePaint)
        drawText(canvas, title, 0f, centerY - radius * 0.022f, radius * 0.048f, mutedColor)
        drawFittedText(canvas, value, 0f, centerY + radius * 0.083f, radius * 0.052f, radius * 1.18f, brightColor, bold = true)
    }

    private fun drawRoundButton(canvas: Canvas, x: Float, y: Float, label: String) {
        fillPaint.color = Color.rgb(55, 45, 29)
        canvas.drawCircle(x, y, radius * 0.096f, fillPaint)
        linePaint.color = accentColor
        linePaint.strokeWidth = radius * 0.007f
        canvas.drawCircle(x, y, radius * 0.096f, linePaint)
        drawText(canvas, label, x, y + radius * 0.041f, radius * 0.112f, brightColor, bold = true)
    }

    private fun drawBottomButton(canvas: Canvas, label: String, centerYFactor: Float) {
        val y = radius * centerYFactor
        val rect = RectF(-radius * 0.435f, y - radius * 0.082f, radius * 0.435f, y + radius * 0.082f)
        fillPaint.color = Color.rgb(49, 39, 23)
        canvas.drawRoundRect(rect, radius * 0.045f, radius * 0.045f, fillPaint)
        linePaint.color = accentColor
        linePaint.strokeWidth = radius * 0.005f
        canvas.drawRoundRect(rect, radius * 0.045f, radius * 0.045f, linePaint)
        drawText(canvas, label, 0f, y + radius * 0.031f, radius * 0.055f, brightColor, bold = true)
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
            fillPaint.color = if (selected) Color.rgb(61, 46, 26) else panelColor
            canvas.drawRoundRect(rect, radius * 0.035f, radius * 0.035f, fillPaint)
            linePaint.color = if (selected) accentColor else panelEdgeColor
            linePaint.strokeWidth = radius * 0.005f
            canvas.drawRoundRect(rect, radius * 0.035f, radius * 0.035f, linePaint)
            drawText(canvas, scene.first, x, y + radius * 0.020f, radius * 0.052f, if (selected) accentColor else brightColor, bold = true)
            drawText(canvas, "EV ${String.format(Locale.US, "%.1f", scene.second)}", x, y + radius * 0.081f, radius * 0.042f, mutedColor)
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
        bold: Boolean = false
    ) {
        val p = if (bold) boldPaint else textPaint
        p.textSize = size
        p.color = color
        p.clearShadowLayer()
        canvas.drawText(text, x, baselineY, p)
    }

    private fun textWidth(text: String, size: Float, bold: Boolean): Float {
        val p = if (bold) boldPaint else textPaint
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
                editingParameter != null -> 135L
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
                if (!longPressHandled && pendingLongPress != null && moved > ViewConfiguration.get(context).scaledTouchSlop) {
                    cancelPendingLongPress()
                    // During an already-open edit session, a normal circular drag adjusts the dial.
                    if (editingParameter != null && pressParameter == editingParameter) {
                        gestureTracking = true
                        lastFingerAngle = angleDegrees(downX - cx, downY - cy)
                        angleAccumulator = 0f
                        updateEditTimeout()
                        updateDialFromMotion(event.x - cx, event.y - cy)
                    }
                } else if (longPressHandled && editingParameter != null && gestureTracking) {
                    updateDialFromMotion(event.x - cx, event.y - cy)
                } else if (gestureTracking && editingParameter != null) {
                    updateDialFromMotion(event.x - cx, event.y - cy)
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
                    if (hypot(dx, dy) < radius * 0.405f || isEiPlateHit(dx, dy)) {
                        openSettings()
                    }
                } else if (editingParameter != null && pressParameter != null) {
                    // Lifting the finger ends a drag, not the edit session.
                    updateEditTimeout()
                }
                gestureTracking = false
                pressParameter = null
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelPendingLongPress()
                gestureTracking = false
                pressParameter = null
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** Enlarged invisible touch bands around each visible dial; no virtual bezel is used. */
    private fun dialAt(dx: Float, dy: Float): ExposurePriority? {
        val distance = hypot(dx, dy) / radius
        // The two invisible hit bands meet between the printed label tracks.
        // This gives each dial a wider target without ambiguous overlap.
        val apertureHit = distance in 0.715f..1.005f
        val shutterHit = distance in 0.405f..0.715f
        return when {
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
        val stepAngle = 360f / count
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
            if (y > 0.62f && abs(x) < 0.55f) {
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
            y > 0.68f && abs(x) < 0.53f -> {
                if (state.source == MeterSource.MANUAL_EV) settingsPage = 1 else closeSettings()
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
