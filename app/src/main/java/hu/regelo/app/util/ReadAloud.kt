package hu.regelo.app.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Felolvasás a telefon beépített szövegfelolvasójával (magyar hanggal, ha telepítve van).
 * A visszahívások mindig a fő szálon érkeznek.
 */
class ReadAloud(context: Context, private val listener: Listener) {
    interface Listener {
        fun onReady(hungarian: Boolean)
        fun onDone(utteranceId: String)
        fun onError(message: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    var ready: Boolean = false
        private set
    var hungarian: Boolean = false
        private set

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            main.post {
                val engine = tts
                if (status != TextToSpeech.SUCCESS || engine == null) {
                    listener.onError("A felolvasó nem indítható el ezen a telefonon.")
                    return@post
                }
                val hu = Locale.forLanguageTag("hu-HU")
                val result = runCatching { engine.setLanguage(hu) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
                hungarian = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { if (utteranceId != null) main.post { listener.onDone(utteranceId) } }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { main.post { listener.onError("Hiba történt felolvasás közben.") } }
                    override fun onError(utteranceId: String?, errorCode: Int) { main.post { listener.onError("Hiba történt felolvasás közben.") } }
                    override fun onStop(utteranceId: String?, interrupted: Boolean) {}
                })
                ready = true
                listener.onReady(hungarian)
            }
        }
    }

    fun speak(id: String, text: String, rate: Float) {
        val engine = tts ?: return
        if (!ready) return
        engine.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        ready = false
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
    }

    companion object {
        /** A rendszer szövegfelolvasó-beállításai (hang telepítéséhez). */
        fun settingsIntent(): Intent = Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Felolvasható szöveg: a jelölők nélkül. */
        fun speakable(text: String): String = inlineMarkdown(text).text.replace(Regex("\\s+"), " ").trim()
    }
}
