package com.djportablepro.app.ui.deck

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.djportablepro.app.R

/**
 * Control de Pitch fader de ultra alta resolución de 14-bits (16,384 niveles de micro-precisión).
 * Ajusta la velocidad/rate de reproducción del audio en rango amplio de ±50% (0.50x a 1.50x).
 */
class PitchControlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val faderCap: View
    private val faderContainer: View
    private val btnReset: View
    private var lastTouchY = 0f
    private var maxTravel = 0f

    private var currentPlaybackRate: Float = 1.0f

    var onPitchChangeListener: ((playbackRate: Float, pitch14Bit: Int) -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_pitch_control, this, true)
        faderCap = findViewById(R.id.faderCap)
        faderContainer = findViewById(R.id.faderContainer)
        btnReset = findViewById(btnResetId())
        updateCenterFeedback(0f)

        btnReset.setOnClickListener {
            currentPlaybackRate = 1.0f
            updateFaderPosition()
            updatePitchValue(0f)
        }
    }

    private fun btnResetId(): Int = R.id.btnReset

    fun setTrackLineColor(colorInt: Int) {
        val trackLine = findViewById<View>(R.id.pitchTrackLine)
        trackLine?.setBackgroundColor(colorInt)
    }

    /**
     * Programmatically updates the Pitch Fader position on the UI to reflect synchronized rate.
     */
    fun setPlaybackRate(rate: Float) {
        currentPlaybackRate = rate
        updateFaderPosition()
    }

    private fun updateFaderPosition() {
        val norm = ((currentPlaybackRate - 1.0f) / 0.50f).coerceIn(-1f, 1f)
        val targetY = if (maxTravel > 0f) norm * maxTravel else 0f
        faderCap.translationY = targetY
        updateCenterFeedback(targetY)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (faderContainer.height > 0 && faderCap.height > 0) {
            maxTravel = (faderContainer.height - faderCap.height) / 2f
            updateFaderPosition()
        }
    }

    private var lastTapTime = 0L

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val now = System.currentTimeMillis()
                if (now - lastTapTime < 320L) { // Double-Tap Pitch Reset to Center (0.0% / 1.0x)
                    currentPlaybackRate = 1.0f
                    updateFaderPosition()
                    updatePitchValue(0f)
                    lastTapTime = 0L
                    return true
                }
                lastTapTime = now
                lastTouchY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - lastTouchY
                lastTouchY = event.y
                
                var newTranslationY = faderCap.translationY + dy
                if (maxTravel > 0f) {
                    newTranslationY = newTranslationY.coerceIn(-maxTravel, maxTravel)
                }
                faderCap.translationY = newTranslationY
                updateCenterFeedback(newTranslationY)
                updatePitchValue(newTranslationY)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updatePitchValue(translationY: Float) {
        val norm = if (maxTravel > 0f) (translationY / maxTravel).coerceIn(-1f, 1f) else 0f
        // Pitch 14-Bit High Resolution: 0 a 16383 (8192 al centro = 0% de variación)
        val pitch14Bit = ((norm + 1f) * 8191.5f).toInt().coerceIn(0, 16383)
        // Rango de Pitch de 14 bits ampliado a ±50% (Velocidad de 0.50x a 1.50x)
        val playbackRate = 1.0f + (norm * 0.50f)

        onPitchChangeListener?.invoke(playbackRate, pitch14Bit)
    }

    private fun updateCenterFeedback(translationY: Float) {
        val isCentered = Math.abs(translationY) < 4f
        if (isCentered) {
            faderCap.setBackgroundResource(R.drawable.fader_cap_centered)
        } else {
            faderCap.setBackgroundResource(R.drawable.fader_cap)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
