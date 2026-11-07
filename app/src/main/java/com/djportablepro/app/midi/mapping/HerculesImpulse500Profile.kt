package com.djportablepro.app.midi.mapping

import com.djportablepro.app.core.Deck
import com.djportablepro.app.core.JogSource
import com.djportablepro.app.midi.decoder.MidiMessage

/**
 * Native profile for Hercules DJControl Inpulse 500 (and compatible Hercules Inpulse family).
 * Vendor ID: 1784 (0x06F8 Guillemot/Hercules), Product ID: 45355 (0xB10B Inpulse 500)
 */
class HerculesImpulse500Profile(
    override val descriptor: DeviceDescriptor? = null
) : ControllerProfile {

    override val name: String = "Hercules DJControl Inpulse 500"

    private var deckAPitchMsb: Int = 64
    private var deckAPitchLsb: Int = 0
    private var deckBPitchMsb: Int = 64
    private var deckBPitchLsb: Int = 0

    override fun supports(device: DeviceDescriptor): Boolean {
        return (device.vendorId == 1784 && device.productId == 45355) ||
                device.productName.contains("Hercules", ignoreCase = true) ||
                device.productName.contains("Inpulse", ignoreCase = true) ||
                device.manufacturer.contains("Hercules", ignoreCase = true)
    }

    override fun mapInput(message: MidiMessage): DjAction? {
        val status = message.status
        val data1 = message.data1
        val data2 = message.data2
        val rawChannel = status and 0x0F
        val command = status and 0xF0

        val deck = when (status) {
            0x91, 0x81, 0xB1, 0xE1, 0x94, 0xB4 -> Deck.DECK_A
            0x92, 0x82, 0xB2, 0xE2, 0x95, 0xB5 -> Deck.DECK_B
            else -> {
                if (rawChannel == 1) Deck.DECK_A
                else if (rawChannel == 2) Deck.DECK_B
                else Deck.DECK_A
            }
        }

        return when (command) {
            // Note On / Note Off (Buttons & Touches)
            0x90, 0x80 -> {
                val isPressed = (command == 0x90) && (data2 > 0)
                when (status) {
                    0x90, 0x80 -> { // Global / Browser Section
                        when (data1) {
                            0x00, 0x05 -> if (isPressed) DjAction.BrowsePush else null
                            0x03 -> if (isPressed) DjAction.AutoDjToggle else null
                            else -> null
                        }
                    }
                    else -> { // Deck A (0x91/0x94) or Deck B (0x92/0x95)
                        when (data1) {
                            // Official Hercules Inpulse 500 XML: Note 0x08 = Jog Wheel Touch!
                            0x08, 0x30 -> DjAction.JogTouch(deck, JogSource.PLATTER, isPressed)
                            0x0A, 0x31 -> DjAction.JogTouch(deck, JogSource.RING, isPressed)
                            // Official Hercules Inpulse 500 XML: Note 0x03 = Vinyl Button!
                            0x03 -> if (isPressed) DjAction.Vinyl(deck, true) else null
                            0x07 -> if (isPressed) DjAction.PlayPause(deck) else null
                            0x06 -> if (isPressed) DjAction.Cue(deck) else null
                            0x05 -> if (isPressed) DjAction.Sync(deck) else null
                            0x0C -> DjAction.Pfl(deck, isPressed)
                            0x0D -> if (isPressed) DjAction.Load(deck) else null
                            0x01 -> if (isPressed) DjAction.Slip(deck, true) else null
                            0x02 -> if (isPressed) DjAction.Quantize(deck, true) else null
                            0x00 -> if (isPressed) DjAction.BrowsePush else null
                            else -> null
                        }
                    }
                }
            }

            // CC (Control Change - Knobs, Faders, Encoders)
            0xB0 -> {
                when (status) {
                    0xB0, 0xB3 -> { // Global Browser Section / Crossfader
                        when (data1) {
                            0x01, 0x02 -> { // Browser Encoder (XML: Status 0xB0/0xB3 Midino 0x01/0x02)
                                val delta = if (data2 >= 64) data2 - 128 else data2
                                DjAction.BrowseEncoder(delta)
                            }
                            0x00 -> DjAction.Crossfader(data2)
                            else -> null
                        }
                    }
                    else -> { // Deck A (0xB1) or Deck B (0xB2)
                        when (data1) {
                            0x00 -> DjAction.Volume(deck, data2)
                            0x01 -> DjAction.Filter(deck, data2) // Deck Filter Knob (0xB1/0xB2 0x01)
                            0x02 -> DjAction.Low(deck, data2)    // Deck Low EQ
                            0x03 -> DjAction.Mid(deck, data2)    // Deck Mid EQ
                            0x04 -> DjAction.High(deck, data2)   // Deck High EQ
                            0x05 -> DjAction.Gain(deck, data2)   // Deck Gain Knob
                            0x08 -> { // Physical Pitch Fader 14-Bit MSB
                                if (deck == Deck.DECK_A) {
                                    deckAPitchMsb = data2
                                    val val14bit = (deckAPitchMsb shl 7) or deckAPitchLsb
                                    DjAction.Pitch(Deck.DECK_A, val14bit)
                                } else {
                                    deckBPitchMsb = data2
                                    val val14bit = (deckBPitchMsb shl 7) or deckBPitchLsb
                                    DjAction.Pitch(Deck.DECK_B, val14bit)
                                }
                            }
                            0x28 -> { // Physical Pitch Fader 14-Bit LSB
                                if (deck == Deck.DECK_A) {
                                    deckAPitchLsb = data2
                                    val val14bit = (deckAPitchMsb shl 7) or deckAPitchLsb
                                    DjAction.Pitch(Deck.DECK_A, val14bit)
                                } else {
                                    deckBPitchLsb = data2
                                    val val14bit = (deckBPitchMsb shl 7) or deckBPitchLsb
                                    DjAction.Pitch(Deck.DECK_B, val14bit)
                                }
                            }
                            0x09, 0x0A, 0x29, 0x30 -> { // Physical Jog Wheel relative rotation
                                val delta = if (data2 >= 64) data2 - 128 else data2
                                DjAction.JogMove(deck, JogSource.PLATTER, delta)
                            }
                            else -> null
                        }
                    }
                }
            }

            // Pitch Bend command (0xE0..0xEF) from physical Jog Wheel / Platter
            0xE0 -> {
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
