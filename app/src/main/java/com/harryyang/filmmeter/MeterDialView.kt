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
 * Mechanical-inspired, touch-first round meter. A long press on either dial
 * enters an edit state; circular finger movement selects and locks that dial.
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
    private var pressParameter: ExposurePriority? = null
    private var downX = 0f
    private var downY = 0f
    private var longPressFired = false
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
            fontSize = radius * 0.078f,
            selectedIndex = state.exposure.apertureIndex,
            isLocked = apertureLocked,
            isEditing = apertureEditing
        )
        drawDialLabels(
            canvas = canvas,
            labels = ExposureMath.shutterLabels,
            labelRadius = innerLabelR,
            rotation = shutterRotation,
            fontSize = radius * 0.064f,
            selectedIndex = state.exposure.shutterIndex,
            isLocked = shutterLocked,
            isEditing = shutterEditing
        )

        // Small legends identify the two independently editable controls.
        drawText(canvas, "F", -radius * 0.705f, radius * 0.045f, radius * 0.043f, mutedColor, bold = true)
        drawText(canvas, "T", -radius * 0.485f, radius * 0.045f, radius * 0.043f, mutedColor, bold = true)

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
        isLocked: Boolean,
        isEditing: Boolean
    ) {
        for (i in labels.indices) {
            val angleDeg = -90f + i * (360f / labels.size) + rotation
            val normalized = normalizeAngle(angleDeg)
            if (normalized < 22f || normalized > 338f) continue
            val angle = Math.toRadians(angleDeg.toDouble())
            val x = (cos(angle) * labelRadius).toFloat()
            val y = (sin(angle) * labelRadius).toFloat()
            val selected = i == selectedIndex
            if (selected) {
                val width = textWidth(labels[i], fontSize * 1.13f, bold = true) * 0.5f + radius * 0.035f
                val height = fontSize * 0.82f
                scratchRect.set(x - width, y - height * 0.60f, x + width, y + height * 0.58f)
                fillPaint.color = if (isEditing) Color.rgb(77, 54, 25) else if (isLocked) Color.rgb(56, 45, 28) else panelColor
                if (isEditing) fillPaint.setShadowLayer(radius * (0.06f + highlightPulse * 0.08f), 0f, 0f, accentColor)
                canvas.drawRoundRect(scratchRect, radius * 0.030f, radius * 0.030f, fillPaint)
                fillPaint.clearShadowLayer()
                linePaint.color = if (isEditing || isLocked) accentColor else panelEdgeColor
                linePaint.strokeWidth = radius * if (isEditing) 0.009f else 0.005f
                canvas.drawRoundRect(scratchRect, radius * 0.030f, radius * 0.030f, linePaint)
            }
            val labelColor = if (selected) brightColor else if (isLocked) mutedColor else brightColor
            drawText(
                canvas, labels[i], x, y + fontSize * 0.34f,
                if (selected) fontSize * 1.13f else fontSize,
                if (selected && (isLocked || isEditing)) accentColor else labelColor,
                bold = selected
            )
        }
    }

    private fun drawCentralHub(canvas: Canvas) {
        val hubR = radius * 0.385f
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
        drawText(canvas, topLabel, 0f, -radius * 0.205f, radius * 0.044f, mutedColor)

        val mainReading = when {
            hasAutoReading -> formatLux(requireNotNull(state.lux))
            isWaiting -> "-- lx"
            else -> "EV ${String.format(Locale.US, "%.1f", state.manualEv100)}"
        }
        drawText(canvas, mainReading, 0f, -radius * 0.065f, radius * 0.112f, brightColor, bold = true)
        val evText = if (isWaiting) "WAITING FOR LIGHT" else "EV100  ${String.format(Locale.US, "%.1f", state.effectiveEv100)}"
        drawText(canvas, evText, 0f, radius * 0.075f, radius * 0.044f, mutedColor)

        val exposure = state.exposure
        val combination = "f/${ExposureMath.formatAperture(exposure.aperture)}  ${formatShutter(exposure.shutterSeconds)}"
        drawText(canvas, combination, 0f, radius * 0.205f, radius * 0.071f, accentColor, bold = true)

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
            radius * 0.300f,
            radius * 0.034f,
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
        drawText(canvas, "EI / ISO", scratchRect.centerX(), top + height * 0.35f, radius * 0.036f, mutedColor)
        drawText(canvas, state.ei.toString(), scratchRect.centerX(), top + height * 0.79f, radius * 0.074f, brightColor, bold = true)
    }

    private fun drawInteractionHint(canvas: Canvas) {
        val label = when (editingParameter) {
            ExposurePriority.APERTURE -> "TURN TO SET F-NUMBER"
            ExposurePriority.SHUTTER -> "TURN TO SET SHUTTER"
            else -> "LONG PRESS A DIAL  ·  TAP CENTER FOR MENU"
        }
        drawText(canvas, label, 0f, radius * 0.975f, radius * 0.040f, mutedColor)
    }

    private fun drawSettings(canvas: Canvas) {
        drawMenuFrame(canvas, "METER SETTINGS", "TOUCH − / + TO CHANGE")
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
        drawText(canvas, title, 0f, -radius * 0.705f, radius * 0.060f, brightColor, bold = true)
        drawText(canvas, subtitle, 0f, -radius * 0.575f, radius * 0.035f, mutedColor)
        fillPaint.color = Color.rgb(48, 39, 25)
        canvas.drawCircle(radius * 0.67f, -radius * 0.60f, radius * 0.085f, fillPaint)
        linePaint.color = accentColor
        linePaint.strokeWidth = radius * 0.006f
        canvas.drawCircle(radius * 0.67f, -radius * 0.60f, radius * 0.085f, linePaint)
        drawText(canvas, "×", radius * 0.67f, -radius * 0.568f, radius * 0.090f, accentColor, bold = true)
    }

    private fun drawStepRow(canvas: Canvas, title: String, value: String, centerYFactor: Float) {
        val centerY = radius * centerYFactor
        val rect = RectF(-radius * 0.755f, centerY - radius * 0.116f, radius * 0.755f, centerY + radius * 0.116f)
        fillPaint.color = panelColor
        canvas.drawRoundRect(rect, radius * 0.045f, radius * 0.045f, fillPaint)
        linePaint.color = panelEdgeColor
        linePaint.strokeWidth = radius * 0.004f
        canvas.drawRoundRect(rect, radius * 0.045f, radius * 0.045f, linePaint)
        drawText(canvas, title, 0f, centerY - radius * 0.018f, radius * 0.039f, mutedColor)
        drawText(canvas, value, 0f, centerY + radius * 0.070f, radius * 0.068f, brightColor, bold = true)
        drawRoundButton(canvas, -radius * 0.565f, centerY, "−")
        drawRoundButton(canvas, radius * 0.565f, centerY, "+")
    }

    private fun drawToggleRow(canvas: Canvas, title: String, value: String, centerYFactor: Float, active: Boolean) {
        val centerY = radius * centerYFactor
        val rect = RectF(-radius * 0.755f, centerY - radius * 0.116f, radius * 0.755f, centerY + radius * 0.116f)
        fillPaint.color = if (active) Color.rgb(38, 34, 25) else panelColor
        canvas.drawRoundRect(rect, radius * 0.045f, radius * 0.045f, fillPaint)
        linePaint.color = if (active) accentColor else panelEdgeColor
        linePaint.strokeWidth = radius * 0.006f
        canvas.drawRoundRect(rect, radius * 0.045f, radius * 0.045f, linePaint)
        drawText(canvas, title, 0f, centerY - radius * 0.018f, radius * 0.039f, mutedColor)
        drawText(canvas, value, 0f, centerY + radius * 0.070f, radius * 0.068f, if (active) accentColor else brightColor, bold = true)
    }

    private fun drawInfoRow(canvas: Canvas, title: String, value: String, centerYFactor: Float) {
        val centerY = radius * centerYFactor
        val rect = RectF(-radius * 0.755f, centerY - radius * 0.116f, radius * 0.755f, centerY + radius * 0.116f)
        fillPaint.color = panelColor
        canvas.drawRoundRect(rect, radius * 0.045f, radius * 0.045f, fillPaint)
        linePaint.color = panelEdgeColor
        linePaint.strokeWidth = radius * 0.004f
        canvas.drawRoundRect(rect, radius * 0.045f, radius * 0.045f, linePaint)
        drawText(canvas, title, 0f, centerY - radius * 0.018f, radius * 0.039f, mutedColor)
        drawText(canvas, value, 0f, centerY + radius * 0.070f, radius * 0.040f, brightColor, bold = true)
    }

    private fun drawRoundButton(canvas: Canvas, x: Float, y: Float, label: String) {
        fillPaint.color = Color.rgb(55, 45, 29)
        canvas.drawCircle(x, y, radius * 0.080f, fillPaint)
        linePaint.color = accentColor
        linePaint.strokeWidth = radius * 0.006f
        canvas.drawCircle(x, y, radius * 0.080f, linePaint)
        drawText(canvas, label, x, y + radius * 0.035f, radius * 0.095f, brightColor, bold = true)
    }

    private fun drawBottomButton(canvas: Canvas, label: String, centerYFactor: Float) {
        val y = radius * centerYFactor
        val rect = RectF(-radius * 0.48f, y - radius * 0.065f, radius * 0.48f, y + radius * 0.065f)
        fillPaint.color = Color.rgb(49, 39, 23)
        canvas.drawRoundRect(rect, radius * 0.045f, radius * 0.045f, fillPaint)
        linePaint.color = accentColor
        linePaint.strokeWidth = radius * 0.005f
        canvas.drawRoundRect(rect, radius * 0.045f, radius * 0.045f, linePaint)
        drawText(canvas, label, 0f, y + radius * 0.025f, radius * 0.047f, brightColor, bold = true)
    }

    private fun drawScenePicker(canvas: Canvas) {
        drawMenuFrame(canvas, "SCENE PRESETS", "SELECT A STARTING EXPOSURE")
        val columnX = listOf(-radius * 0.36f, radius * 0.36f)
        val rowY = listOf(-radius * 0.35f, -radius * 0.02f, radius * 0.31f)
        scenePresets.forEachIndexed { index, scene ->
            val x = columnX[index % 2]
            val y = rowY[index / 2]
            val rect = RectF(x - radius * 0.315f, y - radius * 0.105f, x + radius * 0.315f, y + radius * 0.105f)
            val selected = state.source == MeterSource.MANUAL_EV && abs(state.manualEv100 - scene.second) < 0.01
            fillPaint.color = if (selected) Color.rgb(61, 46, 26) else panelColor
            canvas.drawRoundRect(rect, radius * 0.035f, radius * 0.035f, fillPaint)
            linePaint.color = if (selected) accentColor else panelEdgeColor
            linePaint.strokeWidth = radius * 0.005f
            canvas.drawRoundRect(rect, radius * 0.035f, radius * 0.035f, linePaint)
            drawText(canvas, scene.first, x, y + radius * 0.018f, radius * 0.048f, if (selected) accentColor else brightColor, bold = true)
            drawText(canvas, "EV ${String.format(Locale.US, "%.1f", scene.second)}", x, y + radius * 0.070f, radius * 0.035f, mutedColor)
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
                longPressFired = false
                pressParameter = dialAt(event.x - cx, event.y - cy)
                val parameter = pressParameter
                if (parameter != null) {
                    val task = Runnable { beginDialEdit(parameter) }
                    pendingLongPress = task
                    handler.postDelayed(task, ViewConfiguration.getLongPressTimeout().toLong())
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (settingsOpen) return true
                if (!longPressFired && pressParameter != null) {
                    val moved = hypot(event.x - downX, event.y - downY)
                    if (moved > ViewConfiguration.get(context).scaledTouchSlop) cancelPendingLongPress()
                } else if (longPressFired) {
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
                if (longPressFired) {
                    finishDialEdit()
                } else if (pressParameter == null) {
                    val dx = event.x - cx
                    val dy = event.y - cy
                    if (hypot(dx, dy) < radius * 0.405f || isEiPlateHit(dx, dy)) {
                        openSettings()
                    }
                }
                pressParameter = null
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelPendingLongPress()
                if (longPressFired) finishDialEdit()
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

    private fun dialAt(dx: Float, dy: Float): ExposurePriority? {
        val distance = hypot(dx, dy)
        return when {
            distance in (radius * 0.715f)..(radius * 0.975f) -> ExposurePriority.APERTURE
            distance in (radius * 0.485f)..(radius * 0.710f) -> ExposurePriority.SHUTTER
            else -> null
        }
    }

    private fun isEiPlateHit(dx: Float, dy: Float): Boolean =
        dx in (radius * 0.52f)..(radius * 0.93f) && abs(dy) < radius * 0.15f

    private fun beginDialEdit(parameter: ExposurePriority) {
        val selectedIndex = when (parameter) {
            ExposurePriority.APERTURE -> state.exposure.apertureIndex
            ExposurePriority.SHUTTER -> state.exposure.shutterIndex
            ExposurePriority.NONE -> 0
        }
        longPressFired = true
        editingParameter = parameter
        angleAccumulator = 0f
        lastFingerAngle = angleDegrees(downX - cx, downY - cy)
        val count = if (parameter == ExposurePriority.APERTURE) ExposureMath.apertureValues.size else ExposureMath.shutterSeconds.size
        state = state.copy(priority = parameter, lockedIndex = selectedIndex.coerceIn(0, count - 1))
        calculateTargetRotations(animate = true)
        startHighlightPulse()
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

    private fun finishDialEdit() {
        if (editingParameter != null) {
            editingParameter = null
            stopHighlightPulse()
            onStateChanged(state)
        }
        longPressFired = false
        pressParameter = null
    }

    private fun cancelPendingLongPress() {
        pendingLongPress?.let { handler.removeCallbacks(it) }
        pendingLongPress = null
    }

    private fun angleDegrees(dx: Float, dy: Float): Float = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

    private fun normalizeAngle(value: Float): Float = ((value % 360f) + 360f) % 360f

    private fun openSettings() {
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
                y in -0.48f..-0.20f -> 0
                y in -0.15f..0.13f -> 1
                y in 0.18f..0.44f -> 2
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

        when {
            y in -0.46f..-0.23f -> adjustEi(if (x < 0f) -1 else 1)
            y in -0.17f..0.06f -> adjustCalibration(if (x < 0f) -1 else 1)
            y in 0.12f..0.36f -> toggleSource()
            y in 0.40f..0.65f && state.source == MeterSource.MANUAL_EV -> adjustManualEv(if (x < 0f) -1 else 1)
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
        apertureAnimator?.cancel()
        shutterAnimator?.cancel()
        stopHighlightPulse()
        super.onDetachedFromWindow()
    }
}
