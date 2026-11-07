package com.djportablepro.app.midi

import com.djportablepro.app.core.Deck
import com.djportablepro.app.core.JogSource
import com.djportablepro.app.midi.decoder.MidiMessage
import com.djportablepro.app.midi.mapping.DjAction
import com.djportablepro.app.midi.mapping.HerculesImpulse500Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiMappingTest {

    @Test
    fun `moving physical jog wheel triggers JogMove and not Pitch`() {
        val profile = HerculesImpulse500Profile()

        // 1. Jog Wheel Top Platter relative movement (CC 0xB1, 0x09)
        val msgPlatterTop = MidiMessage(0xB1, 0x09, 65) // CC data2 >= 64 is negative delta -63
        val action1 = profile.mapInput(msgPlatterTop)
        assertTrue("Expected JogMove but got $action1", action1 is DjAction.JogMove)
        val jogAction1 = action1 as DjAction.JogMove
        assertEquals(Deck.DECK_A, jogAction1.deck)
        assertEquals(JogSource.PLATTER, jogAction1.source)
        assertEquals(-63, jogAction1.delta)

        // 2. Pitch Bend 0xE0 / 0xE1 command sent by physical platter/jog wheel
        val msgPitchBend = MidiMessage(0xE1, 0x00, 0x48) // Pitch bend value > 8192
        val action2 = profile.mapInput(msgPitchBend)
        assertTrue("Expected JogMove from pitch bend command but got $action2", action2 is DjAction.JogMove)
        val jogAction2 = action2 as DjAction.JogMove
        assertEquals(Deck.DECK_A, jogAction2.deck)
        assertEquals(JogSource.PLATTER, jogAction2.source)
    }

    @Test
    fun `moving physical pitch fader triggers Pitch and not JogMove`() {
        val profile = HerculesImpulse500Profile()

        // Physical Pitch Fader MSB on CC 0xB1, 0x08
        val msgPitchMsb = MidiMessage(0xB1, 0x08, 96) // MSB = 96
        val action1 = profile.mapInput(msgPitchMsb)
        assertTrue("Expected Pitch but got $action1", action1 is DjAction.Pitch)
        val pitchAction1 = action1 as DjAction.Pitch
        assertEquals(Deck.DECK_A, pitchAction1.deck)
        assertEquals(96 shl 7, pitchAction1.value)

        // Physical Pitch Fader LSB on CC 0xB1, 0x28
        val msgPitchLsb = MidiMessage(0xB1, 0x28, 32) // LSB = 32
        val action2 = profile.mapInput(msgPitchLsb)
        assertTrue("Expected Pitch but got $action2", action2 is DjAction.Pitch)
        val pitchAction2 = action2 as DjAction.Pitch
        assertEquals(Deck.DECK_A, pitchAction2.deck)
        assertEquals((96 shl 7) or 32, pitchAction2.value)
    }

    @Test
    fun `deck B pitch and jog mappings operate independently`() {
        val profile = HerculesImpulse500Profile()

        // Deck B Pitch Fader (CC 0xB2, 0x08)
        val msgPitchB = MidiMessage(0xB2, 0x08, 48)
        val actionPitchB = profile.mapInput(msgPitchB)
        assertTrue("Expected Deck B Pitch but got $actionPitchB", actionPitchB is DjAction.Pitch)
        assertEquals(Deck.DECK_B, (actionPitchB as DjAction.Pitch).deck)

        // Deck B Jog Wheel Platter (CC 0xB2, 0x29)
        val msgJogB = MidiMessage(0xB2, 0x29, 2)
        val actionJogB = profile.mapInput(msgJogB) as DjAction.JogMove
        assertEquals(Deck.DECK_B, actionJogB.deck)
        assertEquals(2, actionJogB.delta)
    }

    @Test
    fun `touching physical jog wheel triggers JogTouch`() {
        val profile = HerculesImpulse500Profile()

        // Touch On Deck A (Status 0x91, Note 0x08, Velocity 127)
        val msgTouchOn = MidiMessage(0x91, 0x08, 127)
        val actionOn = profile.mapInput(msgTouchOn)
        assertTrue("Expected JogTouch but got $actionOn", actionOn is DjAction.JogTouch)
        val touchActionOn = actionOn as DjAction.JogTouch
        assertEquals(Deck.DECK_A, touchActionOn.deck)
        assertEquals(JogSource.PLATTER, touchActionOn.source)
        assertTrue(touchActionOn.touched)

        // Touch On Deck B (Status 0x92, Note 0x08, Velocity 127)
        val msgTouchOnB = MidiMessage(0x92, 0x08, 127)
        val actionOnB = profile.mapInput(msgTouchOnB)
        assertTrue("Expected JogTouch B but got $actionOnB", actionOnB is DjAction.JogTouch)
        val touchActionOnB = actionOnB as DjAction.JogTouch
        assertEquals(Deck.DECK_B, touchActionOnB.deck)
        assertEquals(JogSource.PLATTER, touchActionOnB.source)
        assertTrue(touchActionOnB.touched)
    }
}
