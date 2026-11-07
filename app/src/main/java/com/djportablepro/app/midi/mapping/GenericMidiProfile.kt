package com.djportablepro.app.midi.mapping

import com.djportablepro.app.core.Deck
import com.djportablepro.app.core.JogSource
import com.djportablepro.app.midi.decoder.MidiMessage

/**
 * Generic MIDI mapping meant to work with controllers that do not have a custom profile yet.
 */
class GenericMidiProfile(
    override val descriptor: DeviceDescriptor? = null
) : ControllerProfile {

    override val name: String = descriptor?.productName ?: "Generic MIDI Controller"

    override fun supports(device: DeviceDescriptor): Boolean {
        if (descriptor == null) return true
        return descriptor.vendorId == device.vendorId && descriptor.productId == device.productId
    }

    override fun mapInput(message: MidiMessage): DjAction? {
        val command = message.status and 0xF0
        val rawChannel = message.status and 0x0F
        val data1 = message.data1
        val data2 = message.data2

        val deck = if (rawChannel == 1 || rawChannel == 2) Deck.DECK_B else Deck.DECK_A

        return when (command) {
            0x90, 0x80 -> {
                val isPressed = (command == 0x90) && (data2 > 0)
                when (data1) {
                    0x09, 0x30, 0x36 -> DjAction.JogTouch(deck, JogSource.PLATTER, isPressed)
                    0x0A, 0x31 -> DjAction.JogTouch(deck, JogSource.RING, isPressed)
                    0x07 -> if (isPressed) DjAction.PlayPause(deck) else null
                    0x06 -> if (isPressed) DjAction.Cue(deck) else null
                    0x05 -> if (isPressed) DjAction.Sync(deck) else null
                    else -> null
                }
            }
            0xB0 -> {
                when (data1) {
                    0x00 -> DjAction.Volume(deck, data2)
                    0x01 -> DjAction.Filter(deck, data2)
                    0x02 -> DjAction.Low(deck, data2)
                    0x03 -> DjAction.Mid(deck, data2)
                    0x04 -> DjAction.High(deck, data2)
                    0x05 -> DjAction.Gain(deck, data2)
                    0x08, 0x28 -> { // Pitch fader MSB / LSB
                        val val14bit = (data2 shl 7)
                        DjAction.Pitch(deck, val14bit)
                    }
                    0x09, 0x29 -> { // Jog move Platter
                        val delta = if (data2 >= 64) data2 - 128 else data2
                        DjAction.JogMove(deck, JogSource.PLATTER, delta)
                    }
                    0x0A, 0x30, 0x31 -> { // Jog move Ring
                        val delta = if (data2 >= 64) data2 - 128 else data2
                        DjAction.JogMove(deck, JogSource.RING, delta)
                    }
                    else -> null
                }
            }
            0xE0 -> { // Pitch bend / Jog move
                val val14bit = (data2 shl 7) or data1
                val rawDelta = (val14bit - 8192) / 128
                val delta = if (rawDelta == 0) {
                    if (val14bit > 8192) 1 else if (val14bit < 8192) -1 else 0
                } else rawDelta
                DjAction.JogMove(deck, JogSource.PLATTER, delta)
            }
            else -> null
        }
    }

    override fun mapOutput(state: ControllerOutputState): List<MidiMessage> = emptyList()
}
