package com.djportablepro.app.midi.mapping

import com.djportablepro.app.core.Deck
import com.djportablepro.app.core.JogSource
import com.djportablepro.app.midi.decoder.MidiMessage
import org.json.JSONObject

/**
 * Imports a controller mapping from JSON and turns it into a runtime ControllerProfile.
 *
 * Example JSON schema:
 * {
 *   "name": "Custom Deck",
 *   "vendorId": 1234,
 *   "productId": 5678,
 *   "manufacturer": "Custom",
 *   "productName": "Deck Lite",
 *   "mappings": [
 *     {"type": "note", "status": 145, "data1": 7, "deck": "A", "action": "PlayPause"},
 *     {"type": "cc", "status": 177, "data1": 0, "deck": "A", "action": "Volume"}
 *   ]
 * }
 */
class ControllerProfileImporter {

    fun import(json: String): ControllerProfile {
        val root = JSONObject(json)
        val name = root.optString("name", "Imported Controller")
        val vendorId = root.optInt("vendorId", -1)
        val productId = root.optInt("productId", -1)
        val manufacturer = root.optString("manufacturer", "")
        val productName = root.optString("productName", name)
        val mappings = root.optJSONArray("mappings") ?: org.json.JSONArray()

        val rules = mutableListOf<ImportedRule>()
        for (i in 0 until mappings.length()) {
            val mapping = mappings.getJSONObject(i)
            val type = mapping.optString("type", "note")
            val status = mapping.optInt("status", 0)
            val data1 = mapping.optInt("data1", 0)
            val action = mapping.optString("action", "")
            val deck = mapping.optString("deck", "A")
            rules.add(
                ImportedRule(
                    type = type,
                    status = status,
                    data1 = data1,
                    action = action,
                    deck = parseDeck(deck),
                )
            )
        }

        return ImportedControllerProfile(
            name = name,
            descriptor = DeviceDescriptor(vendorId, productId, manufacturer, productName),
            rules = rules,
        )
    }

    private fun parseDeck(value: String): Deck = when (value.uppercase()) {
        "B" -> Deck.DECK_B
        else -> Deck.DECK_A
    }

    private data class ImportedRule(
        val type: String,
        val status: Int,
        val data1: Int,
        val action: String,
        val deck: Deck,
    )

    private class ImportedControllerProfile(
        override val name: String,
        override val descriptor: DeviceDescriptor,
        private val rules: List<ImportedRule>,
    ) : ControllerProfile {

        override fun supports(device: DeviceDescriptor): Boolean {
            return descriptor.vendorId == device.vendorId && descriptor.productId == device.productId
        }

        override fun mapInput(message: MidiMessage): DjAction? {
            val matchingRule = rules.firstOrNull {
                it.status == message.status && it.data1 == message.data1
            } ?: return null

            val value = message.data2
            return when (matchingRule.action) {
                "PlayPause" -> DjAction.PlayPause(matchingRule.deck)
                "Cue" -> DjAction.Cue(matchingRule.deck)
                "Sync" -> DjAction.Sync(matchingRule.deck)
                "Load" -> DjAction.Load(matchingRule.deck)
                "JogMove" -> DjAction.JogMove(matchingRule.deck, JogSource.PLATTER, value - 64)
                "Volume" -> DjAction.Volume(matchingRule.deck, value)
                "Gain" -> DjAction.Gain(matchingRule.deck, value)
                "High" -> DjAction.High(matchingRule.deck, value)
                "Mid" -> DjAction.Mid(matchingRule.deck, value)
                "Low" -> DjAction.Low(matchingRule.deck, value)
                "Filter" -> DjAction.Filter(matchingRule.deck, value)
                "Pitch" -> DjAction.Pitch(matchingRule.deck, value)
                "LoopIn" -> DjAction.LoopIn(matchingRule.deck)
                "LoopOut" -> DjAction.LoopOut(matchingRule.deck)
                "Pfl" -> DjAction.Pfl(matchingRule.deck, true)
                "Crossfader" -> DjAction.Crossfader(value)
                "BrowseEncoder" -> DjAction.BrowseEncoder(value - 64)
                else -> null
            }
        }

        override fun mapOutput(state: ControllerOutputState): List<MidiMessage> = emptyList()
    }
}
