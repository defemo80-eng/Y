package com.example.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TtsHelper(
    context: Context,
    private val onTtsError: (String) -> Unit = {}
) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingSpeak: Triple<String, String, Float>? = null

    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val spanish = Locale("es", "ES")
                val res = tts?.setLanguage(spanish)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    val fallback = tts?.setLanguage(Locale.getDefault())
                    if (fallback == TextToSpeech.LANG_MISSING_DATA || fallback == TextToSpeech.LANG_NOT_SUPPORTED) {
                        onTtsError("No hay paquetes de voz instalados en este dispositivo.")
                    }
                }
                isInitialized = true
                pendingSpeak?.let { (id, txt, rate) ->
                    pendingSpeak = null
                    speak(id, txt, rate)
                }
            } else {
                Log.w("TtsHelper", "TextToSpeech init failed with status $status")
                onTtsError("El motor de lectura en voz alta no está disponible en este dispositivo.")
            }
        }.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _speakingMessageId.value = utteranceId
                }

                override fun onDone(utteranceId: String?) {
                    if (_speakingMessageId.value == utteranceId) {
                        _speakingMessageId.value = null
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (_speakingMessageId.value == utteranceId) {
                        _speakingMessageId.value = null
                    }
                }
            })
        }
    }

    fun speak(messageId: String, rawText: String, speechRate: Float = 1.0f) {
        if (_speakingMessageId.value == messageId) {
            stop()
            return
        }
        val cleanText = cleanForSpeech(rawText)
        if (cleanText.isBlank()) return

        if (!isInitialized) {
            pendingSpeak = Triple(messageId, rawText, speechRate)
            return
        }

        tts?.setSpeechRate(speechRate.coerceIn(0.6f, 1.6f))
        _speakingMessageId.value = messageId
        val result = tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, messageId)
        if (result == TextToSpeech.ERROR) {
            _speakingMessageId.value = null
            onTtsError("No se pudo iniciar la lectura en voz alta.")
        }
    }

    fun stop() {
        pendingSpeak = null
        tts?.stop()
        _speakingMessageId.value = null
    }

    fun shutdown() {
        pendingSpeak = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        _speakingMessageId.value = null
    }

    private fun cleanForSpeech(text: String): String {
        return text
            .replace(Regex("```[\\s\\S]*?```"), " Bloque de código. ")
            .replace("`", "")
            .replace("**", "")
            .replace("*", "")
            .replace("#", "")
            .trim()
    }
}

class SpeechDictationHelper(
    private val context: Context,
    private val onResultText: (String) -> Unit,
    private val onErrorMessage: (String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening() {
        if (!isAvailable()) {
            onErrorMessage("El servicio de reconocimiento de voz no está instalado en este dispositivo.")
            return
        }
        stopListening()
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _partialText.value = ""
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {
                        _rmsLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        _rmsLevel.value = 0f
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No se detectó voz clara. Inténtalo de nuevo."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tiempo de dictado agotado."
                            SpeechRecognizer.ERROR_AUDIO -> "Error al acceder al micrófono."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Se requiere permiso de micrófono."
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                "Se necesita conexión o paquete de voz en español."
                            else -> "No se pudo completar el dictado (código $error)."
                        }
                        onErrorMessage(msg)
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        _rmsLevel.value = 0f
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull().orEmpty().trim()
                        if (text.isNotEmpty()) {
                            onResultText(text)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        _partialText.value = partial
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            _isListening.value = true
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            _isListening.value = false
            onErrorMessage("No fue posible iniciar el dictado por voz.")
        }
    }

    fun stopListening() {
        try {
            recognizer?.stopListening()
            recognizer?.destroy()
        } catch (_: Exception) {
        } finally {
            recognizer = null
            _isListening.value = false
            _rmsLevel.value = 0f
        }
    }
}
