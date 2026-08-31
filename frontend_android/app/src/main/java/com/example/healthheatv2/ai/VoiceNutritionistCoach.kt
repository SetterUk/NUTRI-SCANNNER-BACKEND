package com.example.healthheatv2.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class VoiceNutritionistCoach(
    private val context: Context,
    private val textCoach: NanoNutritionistCoach
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    
    init {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale("en", "IN") // Indian English
            }
        }
    }

    fun startVoiceInput(onResult: (String) -> Unit, onError: (String) -> Unit = {}) {
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
                onError("Speech recognition error code: $error")
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { onResult(it) }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    suspend fun chatWithVoice(chatHistory: List<com.example.healthheatv2.ui.screens.ChatMessage>, userProfile: com.example.healthheatv2.data.UserProfile, consumedKcal: Float): String = withContext(Dispatchers.Default) {
        // Get text response from coach
        val aiTextResponse = textCoach.generateNutriBotResponse(chatHistory, userProfile, consumedKcal)
        
        // Clean the response so TTS doesn't speak emojis or hidden tags or markdown symbols
        val speechText = aiTextResponse
            .replace(Regex("^(⚡ \\[On-Device Nano\\]|☁️ \\[Cloud AI\\]|🔋 \\[Offline Fallback\\])\\s*\n"), "")
            .replace(Regex("\\[LOG_FOOD:.*?\\]"), "")
            .replace(Regex("\\*\\(.*?\\)\\*"), "") // remove logging confirmation italics
            .replace(Regex("[^a-zA-Z0-9.,?!'\\s]"), " ") // aggressively remove ALL special symbols for TTS, replace with space
            .replace(Regex("\\s+"), " ") // collapse multiple spaces
            .trim()
        
        // Speak the clean response
        textToSpeech?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "ChatResponseId")
        
        return@withContext aiTextResponse
    }

    fun stop() {
        speechRecognizer?.destroy()
        textToSpeech?.shutdown()
    }
}
