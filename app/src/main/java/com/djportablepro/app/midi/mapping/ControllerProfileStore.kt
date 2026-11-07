package com.djportablepro.app.midi.mapping

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Persists imported controller mappings so the app can restore them later and present
 * them to the user as selectable profiles.
 */
object ControllerProfileStore {
    private const val PREFS_NAME = "controller_profile_store"
    private const val KEY_PROFILE_NAMES = "profile_names"
    private const val KEY_PROFILE_PREFIX = "profile_"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveImportedProfile(context: Context, json: String): String {
        val root = JSONObject(json)
        val name = root.optString("name", "Imported Controller")
        val preferences = prefs(context)
        val current = preferences.getStringSet(KEY_PROFILE_NAMES, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(name)
        preferences.edit()
            .putStringSet(KEY_PROFILE_NAMES, current)
            .putString(KEY_PROFILE_PREFIX + name, json)
            .apply()
        return name
    }

    fun listProfiles(context: Context): List<String> {
        val preferences = prefs(context)
        return preferences.getStringSet(KEY_PROFILE_NAMES, emptySet())
            ?.toList()
            ?.sorted()
            ?: emptyList()
    }

    fun loadProfile(context: Context, name: String): String? {
        return prefs(context).getString(KEY_PROFILE_PREFIX + name, null)
    }

    fun deleteProfile(context: Context, name: String) {
        val preferences = prefs(context)
        val current = preferences.getStringSet(KEY_PROFILE_NAMES, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.remove(name)
        preferences.edit()
            .putStringSet(KEY_PROFILE_NAMES, current)
            .remove(KEY_PROFILE_PREFIX + name)
            .apply()
    }
}
