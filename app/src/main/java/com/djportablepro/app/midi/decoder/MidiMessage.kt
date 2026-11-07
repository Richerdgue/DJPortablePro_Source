package com.djportablepro.app.midi.decoder

enum class MidiCommand {
    NOTE_ON,
    NOTE_OFF,
    CONTROL_CHANGE,
    PITCH_BEND,
    AFTERTOUCH,
    UNKNOWN
}

data class MidiMessage(
    val status: Int,
    val data1: Int,
    val data2: Int
) {
    val command: MidiCommand
        get() = when (status and 0xF0) {
            0x90 -> if (data2 > 0) MidiCommand.NOTE_ON else MidiCommand.NOTE_OFF
            0x80 -> MidiCommand.NOTE_OFF
            0xB0 -> MidiCommand.CONTROL_CHANGE
            0xE0 -> MidiCommand.PITCH_BEND
            0xA0, 0xD0 -> MidiCommand.AFTERTOUCH
            else -> MidiCommand.UNKNOWN
        }

    val channel: Int
        get() = status and 0x0F
}
