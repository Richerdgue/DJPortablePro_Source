package com.djportablepro.app.ui.mixer

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.djportablepro.app.R

/**
 * Master Knob component for DJ Portable Pro.
 * Reusable for Gain, EQ, Filter, etc.
 * Supports centralized labeling and Design System integration.
 */
class RotaryDjKnobView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- Properties ---
    var normalizedValue: Float = 0.5f // 0.0 (Min) to 1.0 (Max)
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var label: String = ""
        set(value) {
            field = value
            invalidate()
        }

    var accentColor: Int = Color.CYAN
        set(value) {
            field = value
            updatePaints()
            invalidate()
        }

    var showTickMarks: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    // --- Paint Objects ---
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // --- Cached Geometry ---
    private val ringRect = RectF()
    private val indicatorRect = RectF()
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Range: 270 degrees sweep, starting from 135 degrees (Bottom-Left)
    private val startAngle = 135f
    private val sweepAngle = 270f

    init {
        val defaultAccent = ContextCompat.getColor(context, R.color.dj_cyan)
        val defaultText = ContextCompat.getColor(context, R.color.dj_text_secondary)
        
        val a = context.obtainStyledAttributes(attrs, R.styleable.RotaryDjKnobView, defStyleAttr, 0)
        accentColor = a.getColor(R.styleable.RotaryDjKnobView_accentColor, defaultAccent)
        label = a.getString(R.styleable.RotaryDjKnobView_knobLabel) ?: ""
        normalizedValue = a.getFloat(R.styleable.RotaryDjKnobView_knobValue, 0.5f)
        showTickMarks = a.getBoolean(R.styleable.RotaryDjKnobView_showTickMarks, true)
        a.recycle()

        setupPaints(defaultText)
    }

    private fun setupPaints(textColor: Int) {
        bodyPaint.style = Paint.Style.FILL
        
        ringPaint.style = Paint.Style.STROKE
        ringPaint.strokeCap = Paint.Cap.ROUND
        
        indicatorPaint.style = Paint.Style.FILL
        
        tickPaint.style = Paint.Style.STROKE
        tickPaint.color = ContextCompat.getColor(context, R.color.dj_border_highlight)
        
        labelPaint.color = textColor
        labelPaint.textAlign = Paint.Align.CENTER
        labelPaint.typeface = Typeface.create("sans-serif-condensed-light", Typeface.BOLD)
        labelPaint.isFakeBoldText = true

        shadowPaint.style = Paint.Style.STROKE
        shadowPaint.color = Color.BLACK
        shadowPaint.alpha = 60

        rimPaint.style = Paint.Style.STROKE
        rimPaint.strokeWidth = 1.5f
        rimPaint.color = ContextCompat.getColor(context, R.color.dj_border_highlight)

        updatePaints()
    }

    private fun updatePaints() {
        indicatorPaint.color = accentColor
        ringPaint.color = ContextCompat.getColor(context, R.color.dj_border)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = Math.min(w, h).toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = (size * 0.75f) / 2f // Body radius

        // Body Gradient (3D Metallic effect)
        bodyPaint.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(
                ContextCompat.getColor(context, R.color.dj_surface_active),
                ContextCompat.getColor(context, R.color.dj_surface_control),
                ContextCompat.getColor(context, R.color.dj_panel_bg)
            ),
            floatArrayOf(0f, 0.85f, 1f),
            Shader.TileMode.CLAMP
        )

        labelPaint.textSize = size * 0.16f
        ringPaint.strokeWidth = size * 0.04f
        tickPaint.strokeWidth = size * 0.02f
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val bodyRadius = (Math.min(width, height) * 0.75f) / 2f
        
        // --- Layer 1: Background Ring / Track ---
        ringRect.set(cx - bodyRadius, cy - bodyRadius, cx + bodyRadius, cy + bodyRadius)
        canvas.drawArc(ringRect, startAngle, sweepAngle, false, ringPaint)

        // --- Layer 2: Tick Marks (Visual range reference) ---
        if (showTickMarks) {
            val tickRadiusIn = bodyRadius * 1.15f
            val tickRadiusOut = bodyRadius * 1.30f
            for (i in 0..10) {
                val angle = startAngle + (i / 10f) * sweepAngle
                val rad = Math.toRadians(angle.toDouble())
                canvas.drawLine(
                    (cx + Math.cos(rad) * tickRadiusIn).toFloat(),
                    (cy + Math.sin(rad) * tickRadiusIn).toFloat(),
                    (cx + Math.cos(rad) * tickRadiusOut).toFloat(),
                    (cy + Math.sin(rad) * tickRadiusOut).toFloat(),
                    tickPaint
                )
            }
        }

        // --- Layer 3: Knob Body ---
        canvas.drawCircle(cx, cy, bodyRadius, bodyPaint)
        
        // Sutil rim highlight
        canvas.drawCircle(cx, cy, bodyRadius, rimPaint)

        // --- Layer 4: Position Indicator ---
        val currentAngle = startAngle + (normalizedValue * sweepAngle)
        canvas.save()
        canvas.rotate(currentAngle, cx, cy)
        
        // Rectangular line indicator (Hardware style)
        indicatorRect.set(cx - (bodyRadius * 0.06f), cy - bodyRadius * 0.95f, cx + (bodyRadius * 0.06f), cy - bodyRadius * 0.5f)
        canvas.drawRoundRect(indicatorRect, 2f, 2f, indicatorPaint)
        
        canvas.restore()

        // --- Layer 5: Label ---
        if (label.isNotEmpty()) {
            val labelY = height.toFloat() - labelPaint.descent()
            canvas.drawText(label, cx, labelY, labelPaint)
        }
    }
}
