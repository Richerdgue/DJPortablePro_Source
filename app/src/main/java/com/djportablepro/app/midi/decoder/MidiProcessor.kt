package com.djportablepro.app.midi.decoder

import com.djportablepro.app.midi.mapping.ControllerProfile
import com.djportablepro.app.midi.mapping.ControllerProfileImporter
import com.djportablepro.app.midi.mapping.ControllerProfileRegistry
import com.djportablepro.app.midi.mapping.DeviceDescriptor
import com.djportablepro.app.state.DjEventBus

/**
 * Logical processor that connects raw MIDI data to the DJ system.
 */
class MidiProcessor(
    private val eventBus: DjEventBus
) {
    var activeProfile: ControllerProfile? = ControllerProfileRegistry.defaultProfile()

    fun loadProfile(profile: ControllerProfile) {
        activeProfile = profile
    }

    fun selectProfileForDevice(device: DeviceDescriptor) {
        activeProfile = ControllerProfileRegistry.detect(device)
    }

    fun importProfileFromJson(json: String): ControllerProfile {
        val profile = ControllerProfileImporter().import(json)
        ControllerProfileRegistry.register(profile)
        activeProfile = profile
        return profile
    }

    /**
     * Entry point for raw MIDI data (e.g., from USB).
     */
    fun processRaw(status: Int, data1: Int, data2: Int) {
        val message = MidiMessage(status, data1, data2)
        val action = activeProfile?.mapInput(message)

        if (action != null) {
            eventBus.tryEmit(action)
        }
    }
}
