package com.djportablepro.app.ui.deck

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.djportablepro.app.R

/**
 * Main panel for a Deck (A or B).
 * Manages track info, waveform, jog wheel and transport.
 */
class DeckPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    isRightSide: Boolean = false
) : FrameLayout(context, attrs, defStyleAttr) {

    private val jogWheel: JogWheelView

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.DeckPanel, defStyleAttr, 0)
        val isRight = isRightSide || a.getBoolean(R.styleable.DeckPanel_isRightDeck, false)
        
        val layoutRes = if (isRight) R.layout.view_deck_panel_right else R.layout.view_deck_panel_left
        LayoutInflater.from(context).inflate(layoutRes, this, true)
        
        jogWheel = findViewById(R.id.jogWheel)

        // Delegate attributes to inner components
        if (a.hasValue(R.styleable.DeckPanel_accentColor)) {
            jogWheel.accentColor = a.getColor(R.styleable.DeckPanel_accentColor, 0)
        }
        
        if (a.hasValue(R.styleable.DeckPanel_deckLabel)) {
            jogWheel.deckLabel = a.getString(R.styleable.DeckPanel_deckLabel) ?: ""
        }
        
        a.recycle()
    }
    
    fun setOnJogTouchListener(listener: JogWheelView.OnJogTouchListener) {
        jogWheel.onJogTouchListener = listener
    }

    // API for controlling the deck state
    fun setRotationAngle(angle: Float) {
        jogWheel.rotationAngle = angle
    }

    fun rotateBy(deltaDegrees: Float) {
        jogWheel.rotationAngle = (jogWheel.rotationAngle + deltaDegrees) % 360f
    }

    fun setTrackProgress(progress: Float, remainingTimeMs: Long = Long.MAX_VALUE) {
        jogWheel.trackProgress = progress
        jogWheel.remainingTimeMs = remainingTimeMs
    }

    fun setAlbumArt(bitmap: Bitmap?) {
        jogWheel.albumArtBitmap = bitmap
    }

    fun setPanelBackgroundColor(colorInt: Int) {
        val root = findViewById<ConstraintLayout>(R.id.deckPanelRoot)
        root?.setBackgroundColor(colorInt)
    }

    fun setPitchTrackColor(colorInt: Int) {
        val pitchControl = findViewById<PitchControlView>(R.id.pitchControl)
        pitchControl?.setTrackLineColor(colorInt)
    }
    
    fun setPlaying(playing: Boolean) {
        jogWheel.isPlaying = playing
    }
    
    fun setActive(active: Boolean) {
        jogWheel.isActive = active
    }

    fun setAccentColor(color: Int) {
        jogWheel.accentColor = color
    }

    fun setDeckLabel(label: String) {
        jogWheel.deckLabel = label
    }

    fun setLoopActive(active: Boolean, accentColorInt: Int) {
        val btnLoop = findViewById<Button>(R.id.btnLoop)
        if (btnLoop != null) {
            if (active) {
                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(accentColorInt)
                    setStroke(2, Color.WHITE)
                }
                btnLoop.background = drawable
                btnLoop.setTextColor(Color.BLACK)
            } else {
                btnLoop.background = ContextCompat.getDrawable(context, R.drawable.btn_selector_round)
                btnLoop.setTextColor(ContextCompat.getColor(context, R.color.dj_text_primary))
            }
        }
    }

    fun setOnFxClickListener(listener: () -> Unit) {
        val sideLayout = findViewById<LinearLayout>(R.id.sideButtonsLayout)
        if (sideLayout != null && sideLayout.childCount > 0) {
            sideLayout.getChildAt(0).setOnClickListener { listener() }
        }
    }

    fun setOnEqClickListener(listener: () -> Unit) {
        val sideLayout = findViewById<LinearLayout>(R.id.sideButtonsLayout)
        if (sideLayout != null && sideLayout.childCount > 1) {
            sideLayout.getChildAt(1).setOnClickListener { listener() }
        }
    }

    fun setOnLoopClickListener(listener: () -> Unit) {
        val sideLayout = findViewById<LinearLayout>(R.id.sideButtonsLayout)
        if (sideLayout != null && sideLayout.childCount > 2) {
            sideLayout.getChildAt(2).setOnClickListener { listener() }
        }
    }

    fun setOnPitchChangeListener(listener: (playbackRate: Float, pitch14Bit: Int) -> Unit) {
        val pitchControl = findViewById<PitchControlView>(R.id.pitchControl)
        pitchControl?.onPitchChangeListener = listener
    }

    fun setPlaybackRate(rate: Float) {
        val pitchControl = findViewById<PitchControlView>(R.id.pitchControl)
        pitchControl?.setPlaybackRate(rate)
        jogWheel.playbackRate = rate
    }
}
