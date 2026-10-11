package com.example.personaapp.data

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("persona_prefs", Context.MODE_PRIVATE)

    fun saveApiKey(provider: String, key: String) {
        prefs.edit().putString("${provider.lowercase().replace(" ", "_")}_api_key", key).apply()
    }

    fun getApiKey(provider: String = getActiveProvider()): String {
        val key = "${provider.lowercase().replace(" ", "_")}_api_key"
        // Also support legacy keys
        return prefs.getString(key, "")?.ifEmpty {
            prefs.getString("${provider}_api_key", "")
        } ?: ""
    }
    
    fun saveActiveProvider(provider: String) {
        prefs.edit().putString("active_llm_provider", provider).apply()
    }
    
    fun getActiveProvider(): String {
        return prefs.getString("active_llm_provider", "Gemini Pro") ?: "Gemini Pro"
    }

    fun saveSlmPath(path: String) {
        prefs.edit().putString("slm_model_path", path).apply()
    }

    fun getSlmPath(): String {
        return prefs.getString("slm_model_path", "") ?: ""
    }

    fun saveUserName(name: String) {
        prefs.edit().putString("persona_user_name", name).apply()
    }

    fun getUserName(): String {
        return prefs.getString("persona_user_name", "User") ?: "User"
    }

    fun saveOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean("onboarding_completed", completed).apply()
    }

    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean("onboarding_completed", false)
    }

    fun saveTemperature(temp: Float) {
        prefs.edit().putFloat("ai_temperature", temp).apply()
    }

    fun getTemperature(): Float {
        return prefs.getFloat("ai_temperature", 0.7f)
    }

    fun saveMaxTokens(tokens: Int) {
        prefs.edit().putInt("ai_max_tokens", tokens).apply()
    }

    fun getMaxTokens(): Int {
        return prefs.getInt("ai_max_tokens", 2048)
    }

    fun saveManualOverride(enabled: Boolean) {
        prefs.edit().putBoolean("manual_llm_override", enabled).apply()
    }

    fun isManualOverrideEnabled(): Boolean {
        return prefs.getBoolean("manual_llm_override", false)
    }

    // Lifetime Stats
    fun addLifetimeEventsProcessed(count: Int) {
        val current = prefs.getInt("lifetime_events_processed", 0)
        prefs.edit().putInt("lifetime_events_processed", current + count).apply()
    }
    
    fun getLifetimeEventsProcessed(): Int {
        return prefs.getInt("lifetime_events_processed", 0)
    }

    fun addLifetimeWordsSynthesized(count: Int) {
        val current = prefs.getInt("lifetime_words_synthesized", 0)
        prefs.edit().putInt("lifetime_words_synthesized", current + count).apply()
    }
    
    fun getLifetimeWordsSynthesized(): Int {
        return prefs.getInt("lifetime_words_synthesized", 0)
    }

    fun addLifetimeCoreMemories(count: Int) {
        val current = prefs.getInt("lifetime_core_memories", 0)
        prefs.edit().putInt("lifetime_core_memories", current + count).apply()
    }
    
    fun getLifetimeCoreMemories(): Int {
        return prefs.getInt("lifetime_core_memories", 0)
    }

    // Telemetry Poller High-Watermarks
    fun getLastPolledCallTimestamp(): Long {
        return prefs.getLong("last_polled_call_timestamp", 0L)
    }

    fun saveLastPolledCallTimestamp(timestamp: Long) {
        prefs.edit().putLong("last_polled_call_timestamp", timestamp).apply()
    }

    fun getLastPolledSmsTimestamp(): Long {
        return prefs.getLong("last_polled_sms_timestamp", 0L)
    }

    fun saveLastPolledSmsTimestamp(timestamp: Long) {
        prefs.edit().putLong("last_polled_sms_timestamp", timestamp).apply()
    }

    fun getLastScannedPhotoTimestamp(): Long {
        return prefs.getLong("last_scanned_photo_timestamp", 0L)
    }

    fun saveLastScannedPhotoTimestamp(timestamp: Long) {
        prefs.edit().putLong("last_scanned_photo_timestamp", timestamp).apply()
    }

    fun getLastScannedAudioTimestamp(): Long {
        return prefs.getLong("last_scanned_audio_timestamp", 0L)
    }

    fun saveLastScannedAudioTimestamp(timestamp: Long) {
        prefs.edit().putLong("last_scanned_audio_timestamp", timestamp).apply()
    }

    // Genesis Story & Origin Anchor Preferences
    fun saveGenesisStory(story: String) {
        prefs.edit().putString("genesis_origin_story", story).apply()
    }

    fun getGenesisStory(): String {
        return prefs.getString("genesis_origin_story", "") ?: ""
    }

    fun saveCharacterMotto(motto: String) {
        prefs.edit().putString("character_motto", motto).apply()
    }

    fun getCharacterMotto(): String {
        return prefs.getString("character_motto", "") ?: ""
    }

    fun saveVoiceQuirks(quirks: Set<String>) {
        prefs.edit().putStringSet("voice_quirks", quirks).apply()
    }

    fun getVoiceQuirks(): Set<String> {
        return prefs.getStringSet("voice_quirks", emptySet()) ?: emptySet()
    }

    fun isGenesisStoryConfigured(): Boolean {
        return prefs.getBoolean("genesis_story_configured", false)
    }

    fun setGenesisStoryConfigured(configured: Boolean) {
        prefs.edit().putBoolean("genesis_story_configured", configured).apply()
    }
}

