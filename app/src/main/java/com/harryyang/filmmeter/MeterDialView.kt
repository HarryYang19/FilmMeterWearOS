package com.harryyang.filmmeter

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** Custom-drawn round watch interface. No layout resources or phone-sized widgets. */
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
    private val scenePresets = listOf(
        "DARK INTERIOR" to 4.0,
        "ROOM LIGHT" to 6.0,
        "BRIGHT ROOM" to 8.0,
        "OVERCAST" to 12.0,
        "OPEN SHADE" to 13.0,
        "SUNNY" to 15.0
    )
    private var ambient = false
    private var apertureRotation = 0f
    private var shutterRotation = 0f
    private var apertureAnimator: ValueAnimator? = null
    private var shutterAnimator: ValueAnimator? = null

    val isInSettings: Boolean get() = settingsOpen

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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

    private val backgroundColor = Color.rgb(5, 6, 7)
    private val dialColor = Color.rgb(91, 88, 79)
    private val mutedColor = Color.rgb(125, 125, 119)
    private val brightColor = Color.rgb(229, 220, 197)
    private val accentColor = Color.rgb(211, 173, 100)
    private val panelColor = Color.rgb(17, 18, 18)

    init {
        isFocusable = true
        isClickable = true
        calculateTargetRotations(animate = false)
    }

    fun updateState(newState: MeterState) {
        val old = state
        state = newState
        if (old.exposure.apertureIndex != newState.exposure.apertureIndex ||
            old.exposure.shutterIndex != newState.exposure.shutterIndex ||
            old.ei != newState.ei || old.source != newState.source
        ) {
            calculateTargetRotations(animate = !ambient && old.timestampMs != 0L)
        }
        invalidate()
    }

    fun setAmbientMode(enabled: Boolean) {
        ambient = enabled
        state = state.copy(ambient = enabled)
        apertureAnimator?.cancel()
        shutterAnimator?.cancel()
        if (enabled) {
            // Snap both scales to the selected exposure before entering low-power mode.
            calculateTargetRotations(animate = false)
            // Ambient state uses a sparse grayscale palette and no animated drawing.
            paint.isAntiAlias = false
            fillPaint.isAntiAlias = false
            textPaint.isAntiAlias = false
            boldPaint.isAntiAlias = false
        } else {
            paint.isAntiAlias = true
            fillPaint.isAntiAlias = true
            textPaint.isAntiAlias = true
            boldPaint.isAntiAlias = true
            calculateTargetRotations(animate = false)
        }
        invalidate()
    }

    fun closeSettings() {
        settingsOpen = false
        invalidate()
    }

    fun handleRotary(direction: Int) {
        if (settingsOpen) adjustCurrentSetting(direction) else cycleEi(direction)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        radius = min(w, h) * 0.485f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(if (ambient) Color.BLACK else backgroundColor)
        if (radius <= 0f) return
        val save = canvas.save()
        canvas.translate(cx, cy)
        canvas.drawCircle(0f, 0f, radius, fillPaint.apply { color = if (ambient) Color.BLACK else backgroundColor })
        if (settingsOpen) drawSettings(canvas) else drawMeter(canvas)
        canvas.restoreToCount(save)
    }

    private fun palette(): IntArray = if (ambient) {
        intArrayOf(Color.BLACK, Color.rgb(53, 53, 53), Color.rgb(135, 135, 135), Color.rgb(205, 205, 205), Color.rgb(160, 160, 160))
    } else {
        intArrayOf(backgroundColor, dialColor, mutedColor, brightColor, accentColor)
    }

    private fun drawMeter(canvas: Canvas) {
        val c = palette()
        val bg = c[0]
        val low = c[1]
        val mid = c[2]
        val high = c[3]
        val accent = c[4]
        val outerR = radius * 0.875f
        val innerR = radius * 0.665f
        val centerR = radius * 0.365f

        // Two concentric mechanical dials.
        drawArcWithIsoGap(canvas, outerR, radius * 0.010f, low)
        drawArcWithIsoGap(canvas, innerR, radius * 0.008f, low)
        drawTicks(canvas, outerR, 72, radius * 0.030f, radius * 0.008f, mid, -90f, apertureRotation)
        drawTicks(canvas, innerR, 48, radius * 0.026f, radius * 0.007f, mid, -90f, shutterRotation)
        drawDialLabels(
            canvas,
            ExposureMath.apertureLabels,
            radius * 0.785f,
            apertureRotation,
            high,
            accent,
            radius * 0.052f,
            state.exposure.apertureIndex
        )
        drawDialLabels(
            canvas,
            ExposureMath.shutterLabels,
            radius * 0.585f,
            shutterRotation,
            mid,
            accent,
            radius * 0.040f,
            state.exposure.shutterIndex
        )

        // Fixed top index marker aligns the selected aperture and shutter values.
        paint.color = accent
        paint.strokeWidth = radius * 0.012f
        canvas.drawLine(0f, -radius * 0.965f, 0f, -radius * 0.855f, paint)
        val pointer = Path().apply {
            moveTo(-radius * 0.026f, -radius * 0.855f)
            lineTo(radius * 0.026f, -radius * 0.855f)
            lineTo(0f, -radius * 0.825f)
            close()
        }
        fillPaint.color = accent
        canvas.drawPath(pointer, fillPaint)

        // Central meter hub.
        fillPaint.color = if (ambient) Color.BLACK else panelColor
        canvas.drawCircle(0f, 0f, centerR, fillPaint)
        paint.color = low
        paint.strokeWidth = radius * 0.010f
        canvas.drawCircle(0f, 0f, centerR, paint)
        paint.color = mid
        paint.strokeWidth = radius * 0.004f
        canvas.drawCircle(0f, 0f, centerR * 0.88f, paint)

        val ev = state.effectiveEv100
        val exposure = state.exposure
        val hasAutoReading = state.source == MeterSource.AUTO_SENSOR && state.lux != null
        val isWaiting = state.source == MeterSource.AUTO_SENSOR && state.lux == null
        drawText(
            canvas,
            when { hasAutoReading -> "INCIDENT METER"; isWaiting -> "LIGHT SENSOR"; else -> "MANUAL METER" },
            0f, -radius * 0.225f, radius * 0.035f, mid
        )
        val luxText = when {
            hasAutoReading -> formatLux(requireNotNull(state.lux))
            isWaiting -> "--"
            else -> "EV ${String.format(Locale.US, "%.1f", state.manualEv100)}"
        }
        drawText(canvas, luxText, 0f, -radius * 0.060f, radius * 0.105f, high, bold = true)
        val evLabel = when {
            hasAutoReading -> "EV100 ${String.format(Locale.US, "%.1f", ev)}"
            isWaiting -> "WAITING FOR ALS"
            else -> "EI ${state.ei}  ·  ${if (state.sensorAvailable) "MANUAL" else "NO ALS"}"
        }
        drawText(canvas, evLabel, 0f, radius * 0.085f, radius * 0.040f, mid)
        val exposureLine = if (isWaiting) "--" else "f/${ExposureMath.formatAperture(exposure.aperture)}  ${formatShutterDisplay(exposure.shutterSeconds)}"
        drawText(canvas, exposureLine, 0f, radius * 0.205f, radius * 0.052f, accent, bold = true)
        drawText(canvas, "TAP FOR MENU", 0f, radius * 0.292f, radius * 0.030f, mid)

        // EI plate occupies the deliberately opened right-hand sector between the rings.
        drawIsoPlate(canvas, bg, accent, high, mid)

        if (ambient) {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            drawText(canvas, time, 0f, radius * 0.965f, radius * 0.045f, high, bold = true)
        }
    }

    private fun drawArcWithIsoGap(canvas: Canvas, r: Float, width: Float, color: Int) {
        arcRect.set(-r, -r, r, r)
        paint.color = color
        paint.strokeWidth = width
        // Draw almost the complete scale. The right-side plate interrupts the dial at 3 o'clock.
        canvas.drawArc(arcRect, 22f, 316f, false, paint)
    }

    private fun drawTicks(canvas: Canvas, r: Float, count: Int, length: Float, width: Float, color: Int, start: Float, rotation: Float) {
        paint.color = color
        for (i in 0 until count) {
            val angleDeg = start + i * 360.0 / count + rotation
            val normalized = ((angleDeg % 360.0) + 360.0) % 360.0
            if (normalized < 23.0 || normalized > 337.0) continue
            val angle = Math.toRadians(angleDeg)
            val major = i % (count / 12).coerceAtLeast(1) == 0
            val inner = r - if (major) length * 1.35f else length * 0.62f
            paint.strokeWidth = if (major) width * 1.5f else width * 0.7f
            canvas.drawLine(
                (cos(angle) * inner).toFloat(), (sin(angle) * inner).toFloat(),
                (cos(angle) * r).toFloat(), (sin(angle) * r).toFloat(), paint
            )
        }
    }

    private fun drawDialLabels(
        canvas: Canvas,
        labels: List<String>,
        labelRadius: Float,
        rotation: Float,
        color: Int,
        selectedColor: Int,
        fontSize: Float,
        selectedIndex: Int
    ) {
        for (i in labels.indices) {
            val angleDeg = -90f + i * (360f / labels.size) + rotation
            val normalized = ((angleDeg % 360f) + 360f) % 360f
            if (normalized < 18f || normalized > 342f) continue // leave the EI plate aperture
            val a = Math.toRadians(angleDeg.toDouble())
            val x = (cos(a) * labelRadius).toFloat()
            val y = (sin(a) * labelRadius).toFloat()
            val isSelected = i == selectedIndex
            drawText(
                canvas, labels[i], x, y + fontSize * 0.34f,
                if (isSelected) fontSize * 1.12f else fontSize,
                if (isSelected) selectedColor else color,
                bold = isSelected
            )
        }
    }

    private fun drawIsoPlate(canvas: Canvas, bg: Int, accent: Int, high: Int, mid: Int) {
        val w = radius * 0.285f
        val h = radius * 0.145f
        val left = radius * 0.585f
        val top = -h / 2f
        val rect = RectF(left, top, left + w, top + h)
        fillPaint.color = bg
        canvas.drawRoundRect(rect, radius * 0.018f, radius * 0.018f, fillPaint)
        paint.color = accent
        paint.strokeWidth = radius * 0.008f
        canvas.drawRoundRect(rect, radius * 0.018f, radius * 0.018f, paint)
        drawText(canvas, "EI", rect.centerX(), rect.top + h * 0.31f, radius * 0.030f, mid)
        drawText(canvas, state.ei.toString(), rect.centerX(), rect.top + h * 0.78f, radius * 0.064f, high, bold = true)
    }

    private fun drawSettings(canvas: Canvas) {
        val c = palette()
        val mid = c[2]
        val high = c[3]
        val accent = c[4]
        val centerR = radius * 0.70f
        fillPaint.color = if (ambient) Color.BLACK else backgroundColor
        canvas.drawCircle(0f, 0f, radius, fillPaint)
        paint.color = c[1]
        paint.strokeWidth = radius * 0.012f
        canvas.drawCircle(0f, 0f, centerR, paint)
        paint.color = mid
        paint.strokeWidth = radius * 0.005f
        canvas.drawCircle(0f, 0f, centerR * 0.83f, paint)

        val pageTitle = when (settingsPage) {
            0 -> "FILM EI"
            1 -> "CALIBRATION"
            2 -> "METER SOURCE"
            3 -> "MANUAL EV100"
            4 -> "SCENE PRESET"
            else -> "SENSOR PROFILE"
        }
        drawText(canvas, "FILM METER  /  SETUP", 0f, -radius * 0.40f, radius * 0.047f, mid)
        drawText(canvas, pageTitle, 0f, -radius * 0.20f, radius * 0.052f, accent, bold = true)

        val value = when (settingsPage) {
            0 -> "ISO ${state.ei}"
            1 -> String.format(Locale.US, "%+.1f EV", state.calibrationBiasEv)
            2 -> if (state.source == MeterSource.AUTO_SENSOR) "AUTO SENSOR" else "MANUAL"
            3 -> String.format(Locale.US, "EV %.1f", state.manualEv100)
            4 -> {
                val index = scenePresets.indices.minByOrNull { abs(scenePresets[it].second - state.manualEv100) } ?: 0
                "${scenePresets[index].first} / EV ${String.format(Locale.US, "%.1f", scenePresets[index].second)}"
            }
            else -> when (state.placement) {
                SensorPlacement.WATCH5_PRO_FRONT_SIDE -> "WATCH5 PRO / FRONT"
                SensorPlacement.DISPLAY_TOP -> "DISPLAY TOP"
                SensorPlacement.TOP_BEZEL -> "TOP BEZEL"
                SensorPlacement.DISPLAY_CENTER -> "DISPLAY CENTER"
                SensorPlacement.SIDE_FRAME -> "SIDE FRAME"
            }
        }
        drawText(canvas, value, 0f, radius * 0.02f, radius * 0.095f, high, bold = true)

        val helper = when (settingsPage) {
            0 -> "LEFT / RIGHT  ·  CHANGE EI"
            1 -> "LEFT / RIGHT  ·  ±0.1 STOP"
            2 -> "LEFT / RIGHT  ·  CHANGE SOURCE"
            3 -> "LEFT / RIGHT  ·  ±0.3 STOP"
            4 -> "LEFT / RIGHT  ·  PICK A SCENE"
            else -> if (state.sensorAvailable) "${state.sensorName.take(22)}" else "NO LIGHT SENSOR FOUND"
        }
        drawText(canvas, helper, 0f, radius * 0.22f, radius * 0.034f, mid)
        drawText(canvas, "TAP CENTER  ·  NEXT", 0f, radius * 0.37f, radius * 0.036f, accent)
        drawText(canvas, "BACK KEY  ·  RETURN", 0f, radius * 0.51f, radius * 0.030f, mid)
        if (ambient) {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            drawText(canvas, time, 0f, radius * 0.91f, radius * 0.045f, high, bold = true)
        }
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
        canvas.drawText(text, x, baselineY, p)
    }

    private fun formatLux(lux: Double): String = when {
        lux >= 10_000 -> String.format(Locale.US, "%.1fk lx", lux / 1000.0)
        lux >= 1000 -> String.format(Locale.US, "%.2fk lx", lux / 1000.0)
        lux >= 100 -> String.format(Locale.US, "%.0f lx", lux)
        lux >= 10 -> String.format(Locale.US, "%.1f lx", lux)
        else -> String.format(Locale.US, "%.2f lx", lux)
    }

    private fun formatShutterDisplay(seconds: Double): String = when {
        seconds < 1.0 -> "1/${(1.0 / seconds).toInt()}"
        else -> "${seconds.toInt()}s"
    }

    private fun calculateTargetRotations(animate: Boolean) {
        val exposure = state.exposure
        val newApertureRotation = -exposure.apertureIndex * (360f / ExposureMath.apertureValues.size)
        val newShutterRotation = -exposure.shutterIndex * (360f / ExposureMath.shutterSeconds.size)
        apertureRotation = animateRotation(apertureRotation, newApertureRotation, animate, true)
        shutterRotation = animateRotation(shutterRotation, newShutterRotation, animate, false)
    }

    private fun animateRotation(current: Float, target: Float, animate: Boolean, aperture: Boolean): Float {
        val delta = ((target - current + 540f) % 360f) - 180f
        val finalValue = current + delta
        val animator = ValueAnimator.ofFloat(current, finalValue).apply {
            duration = if (animate) 280L else 0L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val v = it.animatedValue as Float
                if (aperture) apertureRotation = v else shutterRotation = v
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
        return if (animate) current else target
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val dx = event.x - cx
            val dy = event.y - cy
            val distance = hypot(dx, dy)
            val localX = dx
            val localY = dy
            if (settingsOpen) {
                val centerHit = distance < radius * 0.20f || (localY > radius * 0.26f && abs(localX) < radius * 0.42f)
                if (centerHit) {
                    settingsPage = (settingsPage + 1) % 6
                    invalidate()
                } else {
                    adjustCurrentSetting(if (localX < 0) -1 else 1)
                }
            } else if (distance < radius * 0.38f) {
                settingsOpen = true
                settingsPage = 0
                invalidate()
            } else if (localX > radius * 0.53f && localX < radius * 0.96f && abs(localY) < radius * 0.105f) {
                cycleEi(1)
            } else if (distance > radius * 0.39f && localY > radius * 0.40f) {
                settingsOpen = true
                settingsPage = 0
                invalidate()
            }
            performClick()
            return true
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun adjustCurrentSetting(direction: Int) {
        if (direction == 0) return
        val updated = when (settingsPage) {
            0 -> {
                val index = ExposureMath.eiValues.indexOf(state.ei).coerceAtLeast(0)
                state.copy(ei = ExposureMath.eiValues[(index + direction + ExposureMath.eiValues.size) % ExposureMath.eiValues.size])
            }
            1 -> state.copy(calibrationBiasEv = (state.calibrationBiasEv + direction * 0.1).coerceIn(-4.0, 4.0))
            2 -> state.copy(source = if (state.source == MeterSource.AUTO_SENSOR) MeterSource.MANUAL_EV else MeterSource.AUTO_SENSOR)
            3 -> state.copy(manualEv100 = (state.manualEv100 + direction * 0.3).coerceIn(-2.0, 20.0))
            4 -> {
                val currentIndex = scenePresets.indices.minByOrNull { abs(scenePresets[it].second - state.manualEv100) } ?: 0
                val nextIndex = (currentIndex + direction).coerceIn(scenePresets.indices)
                state.copy(source = MeterSource.MANUAL_EV, manualEv100 = scenePresets[nextIndex].second)
            }
            else -> state
        }
        state = updated
        calculateTargetRotations(animate = !ambient)
        onStateChanged(updated)
        invalidate()
    }

    private fun cycleEi(direction: Int) {
        val index = ExposureMath.eiValues.indexOf(state.ei).coerceAtLeast(0)
        val next = (index + direction + ExposureMath.eiValues.size) % ExposureMath.eiValues.size
        val updated = state.copy(ei = ExposureMath.eiValues[next])
        state = updated
        calculateTargetRotations(animate = !ambient)
        onStateChanged(updated)
        invalidate()
    }
}
