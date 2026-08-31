package com.example.healthheatv2.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Locale

class VoiceNutritionistCoach(
    private val context: Context,
    private val textCoach: NanoNutritionistCoach
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isCancelled = false

    private val _speakingMessageText = MutableStateFlow<String?>(null)
    val speakingMessageText = _speakingMessageText.asStateFlow()

    init {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale("en", "IN") // Indian English
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _speakingMessageText.value = utteranceId
                    }

                    override fun onDone(utteranceId: String?) {
                        if (_speakingMessageText.value == utteranceId) {
                            _speakingMessageText.value = null
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (_speakingMessageText.value == utteranceId) {
                            _speakingMessageText.value = null
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (_speakingMessageText.value == utteranceId) {
                            _speakingMessageText.value = null
                        }
                    }
                })
            }
        }
    }

    fun startVoiceInput(onResult: (String) -> Unit, onError: (String) -> Unit = {}) {
        isCancelled = false
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            // Increase silence timeout so it doesn't stop recording while the user pauses
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            
            override fun onError(error: Int) {
                if (isCancelled) return
                // Suppress client cancel, no-match, and timeout codes from emitting user-facing errors
                if (error != SpeechRecognizer.ERROR_CLIENT && 
                    error != SpeechRecognizer.ERROR_NO_MATCH && 
                    error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    onError("Speech recognition error code: $error")
                }
            }

            override fun onResults(results: Bundle?) {
                if (isCancelled) return
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { onResult(it) }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    fun cancelVoiceInput() {
        isCancelled = true
        try {
            speechRecognizer?.setRecognitionListener(null)
            speechRecognizer?.cancel()
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun finishVoiceInput() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cleanTextForSpeech(rawText: String): String {
        val textWithoutThink = if (rawText.contains("</think>")) {
            rawText.substringAfter("</think>")
        } else {
            rawText
        }
        return textWithoutThink
            .replace(Regex("^(⚡ \\[On-Device Nano\\]|☁️ \\[Cloud AI\\]|🔋 \\[Offline Fallback\\])\\s*\n"), "")
            .replace(Regex("\\[LOG_FOOD:.*?\\]"), "")
            .replace(Regex("\\*\\(.*?\\)\\*"), "") // remove logging confirmation italics
            .replace(Regex("[*#_~`]"), "") // remove markdown characters
            .replace(Regex("[^a-zA-Z0-9.,?!'\\s]"), " ") // aggressively remove special symbols for TTS
            .replace(Regex("\\s+"), " ") // collapse multiple spaces
            .trim()
    }

    fun speak(text: String, messageId: String = text) {
        stopTTS()
        val speechText = cleanTextForSpeech(text)
        if (speechText.isBlank()) return
        _speakingMessageText.value = messageId
        textToSpeech?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, messageId)
    }

    fun stopTTS() {
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _speakingMessageText.value = null
    }

    fun toggleTTS(text: String, messageId: String = text) {
        if (_speakingMessageText.value == messageId) {
            stopTTS()
        } else {
            speak(text, messageId)
        }
    }

    suspend fun chatWithVoice(chatHistory: List<com.example.healthheatv2.ui.screens.ChatMessage>, userProfile: com.example.healthheatv2.data.UserProfile, nutritionEngine: com.example.healthheatv2.services.NutritionEngine): String = withContext(Dispatchers.Default) {
        val aiTextResponse = textCoach.generateNutriBotResponse(chatHistory, userProfile, nutritionEngine)
        speak(aiTextResponse, aiTextResponse)
        return@withContext aiTextResponse
    }

    fun stop() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _speakingMessageText.value = null
    }
}
