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
    private val textCoach: LocalNutritionistCoach
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

    suspend fun chatWithVoice(userSpeech: String): String = withContext(Dispatchers.Default) {
        // Get text response from coach
        val response = textCoach.chat(userSpeech)
        
        // Convert to speech
        textToSpeech?.speak(response, TextToSpeech.QUEUE_FLUSH, null, "ChatResponseId")
        
        return@withContext response
    }

    fun stop() {
        speechRecognizer?.destroy()
        textToSpeech?.shutdown()
    }
}
