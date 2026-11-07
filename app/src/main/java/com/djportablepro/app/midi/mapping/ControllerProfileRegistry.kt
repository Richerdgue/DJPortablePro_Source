package com.djportablepro.app.midi.mapping

import com.djportablepro.app.logging.DjLogger

/**
 * Central registry for built-in and imported controller profiles.
 * Handles autodetection and profile selection for controllers connected via USB MIDI.
 */
object ControllerProfileRegistry {
    private val builtInProfiles: List<ControllerProfile> = listOf(
        HerculesImpulse500Profile(),
        GenericMidiProfile()
    )

    private val importedProfiles = mutableMapOf<String, ControllerProfile>()

    fun defaultProfile(): ControllerProfile = builtInProfiles.firstOrNull { it.name.contains("Hercules", ignoreCase = true) }
        ?: builtInProfiles.first()

    fun detect(device: DeviceDescriptor): ControllerProfile {
        val exactMatch = builtInProfiles.firstOrNull { it.supports(device) }
        if (exactMatch != null) {
            DjLogger.d("ControllerProfileRegistry", "Matched built-in controller profile: ${exactMatch.name}")
            return exactMatch
        }

        val importedMatch = importedProfiles.values.firstOrNull { it.supports(device) }
        if (importedMatch != null) {
            DjLogger.d("ControllerProfileRegistry", "Matched imported controller profile: ${importedMatch.name}")
            return importedMatch
        }

        DjLogger.w("ControllerProfileRegistry", "No controller profile matched ${device.vendorId}:${device.productId}; falling back to Generic MIDI")
        return GenericMidiProfile(device)
    }

    fun register(profile: ControllerProfile) {
        importedProfiles[profile.name] = profile
    }

    fun builtIns(): List<ControllerProfile> = builtInProfiles.toList()
}

/**
 * Simple metadata for a connected MIDI controller.
 */
data class DeviceDescriptor(
    val vendorId: Int,
    val productId: Int,
    val manufacturer: String = "",
    val productName: String = "",
)
