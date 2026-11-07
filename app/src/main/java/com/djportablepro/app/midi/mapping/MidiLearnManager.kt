package com.djportablepro.app.midi.mapping

import java.util.concurrent.ConcurrentHashMap

/**
 * Professional MIDI Learn Manager.
 * Allows mapping incoming MIDI messages (Status, Control/Note) to specific DjActions
 * dynamically and persistently.
 */
class MidiLearnManager {

    private val midiToValMap = ConcurrentHashMap<Int, DjAction>()

    private var isLearningActive: Boolean = false
    private var pendingAction: DjAction? = null

    interface LearnListener {
        fun onMidiLearned(action: DjAction, status: Int, data1: Int)
    }

    private var listener: LearnListener? = null

    fun startLearning(action: DjAction, listener: LearnListener) {
        isLearningActive = true
        pendingAction = action
        this.listener = listener
    }

    fun cancelLearning() {
        isLearningActive = false
        pendingAction = null
        listener = null
    }

    fun handleMidiMessage(status: Int, data1: Int, data2: Int): DjAction? {
        val key = (status shl 16) or data1
        if (isLearningActive && pendingAction != null) {
            val action = pendingAction!!
            midiToValMap[key] = action
            listener?.onMidiLearned(action, status, data1)
            cancelLearning()
            return action
        }
        return midiToValMap[key]
    }

    fun clearMapping() {
        midiToValMap.clear()
    }

    fun getMappedActions(): Map<Int, DjAction> = midiToValMap
}
