package com.djportablepro.app.ui.deck

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.djportablepro.app.R

/**
 * Professional Jog Wheel component for DJ Portable Pro.
 * Rendered using Canvas. Needle indicator features high-contrast outline for 100% visibility over any cover art.
 */
class JogWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- Dynamic Properties ---
    var rotationAngle: Float = 0f
        set(value) {
            field = value % 360f
            postInvalidateOnAnimation()
        }

    var trackProgress: Float = 0f // 0.0f to 1.0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            postInvalidateOnAnimation()
        }

    var albumArtBitmap: Bitmap? = null
        set(value) {
            field = value
            postInvalidateOnAnimation()
        }

    var isActive: Boolean = false
        set(value) {
            field = value
            updatePaints()
            postInvalidateOnAnimation()
        }

    var remainingTimeMs: Long = Long.MAX_VALUE
        set(value) {
            val wasNearEnd = field <= 50000L
            val isNearEnd = value <= 50000L
            field = value
            if (isNearEnd && !wasNearEnd) {
                startBlinking()
            } else if (!isNearEnd && wasNearEnd) {
                stopBlinking()
            }
            invalidate()
        }

    private var isBlinkOn = true
    private val blinkRunnable = object : Runnable {
        override fun run() {
            if (remainingTimeMs <= 50000L && remainingTimeMs > 0L) {
                isBlinkOn = !isBlinkOn
                invalidate()
                postDelayed(this, 1000L) // 1 second flash interval
            } else {
                isBlinkOn = true
            }
        }
    }

    private fun startBlinking() {
        removeCallbacks(blinkRunnable)
        isBlinkOn = true
        post(blinkRunnable)
    }

    private fun stopBlinking() {
        removeCallbacks(blinkRunnable)
        isBlinkOn = true
    }

    var playbackRate: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.1f, 3.0f)
        }

    private var lastFrameTimeMs: Long = 0L

    private val rotationRunnable = object : Runnable {
        override fun run() {
            if (isPlaying && trackProgress < 1.0f) {
                val now = SystemClock.uptimeMillis()
                if (lastFrameTimeMs > 0L) {
                    val dt = (now - lastFrameTimeMs) / 1000f
                    // Standard 33.33 RPM vinyl = 360 deg in 1.8s -> 200 degrees per sec
                    val degreesPerSec = 200f * playbackRate
                    rotationAngle = (rotationAngle + degreesPerSec * dt) % 360f
                }
                lastFrameTimeMs = now
                postOnAnimation(this)
            } else {
                lastFrameTimeMs = 0L
                if (isPlaying && trackProgress >= 1.0f) {
                    // Track reached the end: stop playback rotation
                    isPlaying = false
                }
            }
        }
    }

    var isPlaying: Boolean = false
        set(value) {
            val wasPlaying = field
            field = value
            if (value && !wasPlaying) {
                lastFrameTimeMs = SystemClock.uptimeMillis()
                removeCallbacks(rotationRunnable)
                postOnAnimation(rotationRunnable)
            } else if (!value && wasPlaying) {
                lastFrameTimeMs = 0L
                removeCallbacks(rotationRunnable)
            }
            updatePaints()
            invalidate()
        }

    var accentColor: Int = Color.CYAN
        set(value) {
            field = value
            updatePaints()
            invalidate()
        }

    var deckLabel: String = ""
        set(value) {
            field = value
            invalidate()
        }

    // --- Paint Objects ---
    private val chassisPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val platterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accentRingPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val platterRimTouchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val indicatorOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val indicatorLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hubBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // --- Cached Geometry ---
    private val indicatorPath = Path()
    private val progressRect = RectF()
    private val clipPlatterPath = Path()
    private val srcRect = Rect()
    private val destRectF = RectF()

    init {
        val defaultCyan = ContextCompat.getColor(context, R.color.dj_cyan)
        val a = context.obtainStyledAttributes(attrs, R.styleable.JogWheelView, defStyleAttr, 0)
        accentColor = a.getColor(R.styleable.JogWheelView_accentColor, defaultCyan)
        deckLabel = a.getString(R.styleable.JogWheelView_deckLabel) ?: ""
        a.recycle()

        setupPaints()
    }

    // --- Touch Listener ---
    var onJogTouchListener: OnJogTouchListener? = null

    interface OnJogTouchListener {
        fun onTouchDown() {}
        fun onScratch(deltaAngle: Float) {}
        fun onTouchUp() {}
    }

    private var previousTouchAngle = 0f
    private var isTouching = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val centerX = width / 2f
        val centerY = height / 2f
        val x = event.x - centerX
        val y = event.y - centerY
        val currentAngle = Math.toDegrees(Math.atan2(y.toDouble(), x.toDouble())).toFloat()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val distance = Math.hypot(x.toDouble(), y.toDouble()).toFloat()
                val radius = Math.min(width, height) / 2f
                if (distance <= radius * 1.0f) {
                    isTouching = true
                    previousTouchAngle = currentAngle
                    isActive = true
                    onJogTouchListener?.onTouchDown()
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isTouching) {
                    var delta = currentAngle - previousTouchAngle
                    if (delta > 180f) delta -= 360f
                    if (delta < -180f) delta += 360f

                    rotationAngle = (rotationAngle + delta) % 360f
                    onJogTouchListener?.onScratch(delta)
                    previousTouchAngle = currentAngle
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isTouching) {
                    isTouching = false
                    isActive = false
                    onJogTouchListener?.onTouchUp()
                    parent?.requestDisallowInterceptTouchEvent(false)
                    performClick()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun setupPaints() {
        chassisPaint.style = Paint.Style.FILL
        platterPaint.style = Paint.Style.FILL
        accentRingPaint.style = Paint.Style.STROKE
        
        markerPaint.style = Paint.Style.STROKE
        markerPaint.strokeWidth = 1.5f
        markerPaint.color = Color.parseColor("#33FFFFFF")
        
        indicatorPaint.style = Paint.Style.FILL
        
        // Borde exterior negro para 100% de contraste sobre cualquier imagen de carátula
        indicatorOutlinePaint.style = Paint.Style.STROKE
        indicatorOutlinePaint.strokeWidth = 3.5f
        indicatorOutlinePaint.color = Color.BLACK
        indicatorOutlinePaint.strokeJoin = Paint.Join.ROUND

        indicatorLinePaint.style = Paint.Style.STROKE
        indicatorLinePaint.strokeWidth = 2.0f
        indicatorLinePaint.color = Color.WHITE

        hubPaint.style = Paint.Style.FILL
        
        hubBorderPaint.style = Paint.Style.STROKE
        hubBorderPaint.color = Color.parseColor("#66FFFFFF")
        hubBorderPaint.strokeWidth = 2.0f

        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create("sans-serif-condensed-light", Typeface.BOLD)
        
        ledPaint.style = Paint.Style.FILL
        
        updatePaints()
    }

    private fun updatePaints() {
        indicatorPaint.color = accentColor
        accentRingPaint.color = if (isActive) accentColor else Color.parseColor("#1A1A20")
        glowPaint.color = accentColor
        glowPaint.alpha = 210
        platterRimTouchPaint.color = accentColor
        ledPaint.color = if (isActive) Color.WHITE else if (isPlaying) accentColor else Color.parseColor("#22FFFFFF")
        progressRingPaint.color = accentColor
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = Math.min(w, h).toFloat()
        val centerX = w / 2f
        val centerY = h / 2f
        val radius = size / 2f

        chassisPaint.shader = RadialGradient(
            centerX, centerY, radius,
            intArrayOf(Color.parseColor("#32323D"), Color.parseColor("#08080A")),
            floatArrayOf(0.85f, 1.0f),
            Shader.TileMode.CLAMP
        )

        platterPaint.shader = RadialGradient(
            centerX, centerY, radius * 0.88f,
            intArrayOf(Color.parseColor("#121218"), Color.parseColor("#050507")),
            floatArrayOf(0.80f, 1.0f),
            Shader.TileMode.CLAMP
        )

        hubPaint.shader = LinearGradient(
            centerX, centerY - radius * 0.36f, centerX, centerY + radius * 0.36f,
            intArrayOf(Color.parseColor("#2A2A35"), Color.parseColor("#0D0D11"), Color.parseColor("#1A1A22")),
            floatArrayOf(0f, 0.5f, 1.0f),
            Shader.TileMode.CLAMP
        )

        // Aguja/Flecha indicadora prominente
        indicatorPath.reset()
        indicatorPath.moveTo(centerX - radius * 0.035f, centerY - radius * 0.88f)
        indicatorPath.lineTo(centerX + radius * 0.035f, centerY - radius * 0.88f)
        indicatorPath.lineTo(centerX, centerY - radius * 0.52f)
        indicatorPath.close()

        accentRingPaint.strokeWidth = radius * 0.04f
        glowPaint.strokeWidth = radius * 0.12f
        corePaint.strokeWidth = radius * 0.05f
        platterRimTouchPaint.strokeWidth = radius * 0.04f
        progressRingPaint.strokeWidth = radius * 0.06f
        textPaint.textSize = radius * 0.16f

        progressRect.set(
            centerX - radius * 0.94f,
            centerY - radius * 0.94f,
            centerX + radius * 0.94f,
            centerY + radius * 0.94f
        )
    }

    override fun onDraw(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = Math.min(width, height) / 2f

        // --- Layer 1: Outer Chassis ---
        canvas.drawCircle(centerX, centerY, radius, chassisPaint)

        // --- Layer 2: Accent Ring & Touch Neon Glow ---
        if (isActive) {
            // 1. Aura Neón Exterior resplandeciente cuando el Jog es tocado
            canvas.drawCircle(centerX, centerY, radius * 0.94f, glowPaint)
            // 2. Anillo Núcleo Blanco Brillante
            canvas.drawCircle(centerX, centerY, radius * 0.94f, corePaint)
        } else {
            canvas.drawCircle(centerX, centerY, radius * 0.94f, accentRingPaint)
        }

        // --- Layer 2.5: Circular Track Progress Ring ---
        if (trackProgress > 0f) {
            if (remainingTimeMs <= 50000L) {
                progressRingPaint.color = if (isBlinkOn) Color.RED else Color.parseColor("#66FF0000")
            } else {
                progressRingPaint.color = if (isActive) Color.WHITE else accentColor
            }
            progressRingPaint.strokeWidth = if (isActive) radius * 0.08f else radius * 0.05f
            canvas.drawArc(progressRect, -90f, trackProgress * 360f, false, progressRingPaint)
        }

        // --- Layer 3: Position Reference Marks ---
        for (i in 0 until 12) {
            canvas.save()
            canvas.rotate(i * 30f, centerX, centerY)
            canvas.drawLine(centerX, centerY - radius * 0.98f, centerX, centerY - radius * 0.92f, markerPaint)
            canvas.restore()
        }

        // --- Layer 4: Platter Surface (Cover Art en el Vinilo o Disco Oscuro Normal) ---
        val platterRadius = radius * 0.88f
        val hubRadius = radius * 0.36f
        val bmp = albumArtBitmap

        if (bmp != null && !bmp.isRecycled) {
            // Renderizar la carátula en la superficie del vinilo con rotación continua
            val saveCount = canvas.save()
            canvas.rotate(rotationAngle, centerX, centerY)

            clipPlatterPath.reset()
            clipPlatterPath.addCircle(centerX, centerY, platterRadius, Path.Direction.CW)
            canvas.clipPath(clipPlatterPath)

            srcRect.set(0, 0, bmp.width, bmp.height)
            destRectF.set(centerX - platterRadius, centerY - platterRadius, centerX + platterRadius, centerY + platterRadius)
            canvas.drawBitmap(bmp, srcRect, destRectF, null)

            // Surcos de vinilo sobre la carátula
            for (i in 0 until 6) {
                val r = radius * (0.42f + i * 0.08f)
                canvas.drawCircle(centerX, centerY, r, markerPaint)
            }
            canvas.restoreToCount(saveCount)

            // Indicador de aguja/flecha con alto contraste (Borde negro + Relleno de color del deck + Eje blanco)
            canvas.save()
            canvas.rotate(rotationAngle, centerX, centerY)
            canvas.drawPath(indicatorPath, indicatorOutlinePaint)
            canvas.drawPath(indicatorPath, indicatorPaint)
            canvas.drawLine(centerX, centerY - radius * 0.88f, centerX, centerY - radius * 0.54f, indicatorLinePaint)
            canvas.restore()
        } else {
            // Disco de Vinilo Oscuro Normal
            canvas.drawCircle(centerX, centerY, platterRadius, platterPaint)
            for (i in 0 until 8) {
                val r = radius * (0.38f + i * 0.06f)
                canvas.drawCircle(centerX, centerY, r, markerPaint)
            }

            // Indicador de aguja/flecha
            canvas.save()
            canvas.rotate(rotationAngle, centerX, centerY)
            canvas.drawPath(indicatorPath, indicatorOutlinePaint)
            canvas.drawPath(indicatorPath, indicatorPaint)
            canvas.drawLine(centerX, centerY - radius * 0.88f, centerX, centerY - radius * 0.54f, indicatorLinePaint)
            canvas.restore()
        }

        // --- Layer 6: Center Hub (Hub Metalizado con Texto BPM Siempre Legible) ---
        canvas.drawCircle(centerX, centerY, hubRadius, hubPaint)
        canvas.drawCircle(centerX, centerY, hubRadius, hubBorderPaint)

        // Texto de BPM perfectamente visible en el centro
        val textY = centerY - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(deckLabel.ifEmpty { "0.00" }, centerX, textY, textPaint)
        
        // Hub Status LED
        canvas.drawCircle(centerX, centerY + radius * 0.24f, radius * 0.02f, ledPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(rotationRunnable)
        removeCallbacks(blinkRunnable)
    }
}
