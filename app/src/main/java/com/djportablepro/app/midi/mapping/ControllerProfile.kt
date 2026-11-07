package com.djportablepro.app.midi.mapping

import com.djportablepro.app.midi.decoder.MidiMessage

data class ControllerOutputState(
    val masterVuLeft: Int = 0,
    val masterVuRight: Int = 0,
    val deckVu: Int = 0
)

interface ControllerProfile {
    val name: String
    val descriptor: DeviceDescriptor?
    fun supports(device: DeviceDescriptor): Boolean
    fun mapInput(message: MidiMessage): DjAction?
    fun mapOutput(state: ControllerOutputState): List<MidiMessage>
}
