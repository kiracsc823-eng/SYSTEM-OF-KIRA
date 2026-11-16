package com.hunter.system.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Optional text-to-speech. Every call is guarded so a voice failure can never crash the app. */
class SystemVoice(ctx: Context) : TextToSpeech.OnInitListener {
    private val prefs = ctx.getSharedPreferences("hunter_prefs", Context.MODE_PRIVATE)
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    private val pending = ArrayDeque<String>()

    var enabled: Boolean
        get() = prefs.getBoolean("voice", true)
        set(v) { prefs.edit().putBoolean("voice", v).apply() }
    var lastDailyDay: Long
        get() = prefs.getLong("daily_announced", -1)
        set(v) { prefs.edit().putLong("daily_announced", v).apply() }

    init { try { tts = TextToSpeech(ctx.applicationContext, this) } catch (e: Exception) { tts = null } }

    override fun onInit(status: Int) {
        try {
            if (status != TextToSpeech.SUCCESS) { tts = null; return }
            tts?.setLanguage(Locale.US)
            ready = true
            synchronized(pending) { while (pending.isNotEmpty()) say(pending.removeFirst()) }
        } catch (e: Exception) { ready = false }
    }

    private fun say(t: String) { try { tts?.speak(t, TextToSpeech.QUEUE_ADD, null, "hunter") } catch (e: Exception) { } }

    fun speak(text: String) {
        if (!enabled || tts == null) return
        try {
            if (ready) say(text) else synchronized(pending) { if (pending.size < 5) pending.addLast(text) }
        } catch (e: Exception) { }
    }

    fun shutdown() { try { tts?.stop(); tts?.shutdown() } catch (e: Exception) { }; tts = null }
}
