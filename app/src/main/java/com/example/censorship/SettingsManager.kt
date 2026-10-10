package com.example.censorship

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("CensorPrefs", Context.MODE_PRIVATE)

    fun getBadWords(): Set<String> {
        // Defaults to "password" and "secret" if the list is completely empty on first launch
        return prefs.getStringSet("BAD_WORDS", setOf("password", "secret")) ?: setOf()
    }

    fun addBadWord(word: String) {
        val current = getBadWords().toMutableSet()
        current.add(word.lowercase().trim())
        prefs.edit().putStringSet("BAD_WORDS", current).apply()
    }

    fun removeBadWord(word: String) {
        val current = getBadWords().toMutableSet()
        current.remove(word)
        prefs.edit().putStringSet("BAD_WORDS", current).apply()
    }
    
    // Save the Toggle states
    var isVisualCensorOn: Boolean
        get() = prefs.getBoolean("VISUAL_CENSOR", true)
        set(value) = prefs.edit().putBoolean("VISUAL_CENSOR", value).apply()
        
    var isAudioBleeperOn: Boolean
        get() = prefs.getBoolean("AUDIO_BLEEPER", true)
        set(value) = prefs.edit().putBoolean("AUDIO_BLEEPER", value).apply()
        
    var isImageCensorOn: Boolean
        get() = prefs.getBoolean("IMAGE_CENSOR", false)
        set(value) = prefs.edit().putBoolean("IMAGE_CENSOR", value).apply()
        
    // Parental Lock PIN
    var pinCode: String?
        get() = prefs.getString("PIN_CODE", null)
        set(value) = prefs.edit().putString("PIN_CODE", value).apply()
}
