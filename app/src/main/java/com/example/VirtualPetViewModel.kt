package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class PetEmotion {
    IDLE, LISTENING, THINKING, SPEAKING, HAPPY, SURPRISED, SAD, SLEEPY, LOVE, EXCITED, ERROR
}

class VirtualPetViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val _emotion = MutableStateFlow(PetEmotion.IDLE)
    val emotion: StateFlow<PetEmotion> = _emotion.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _chatLog = MutableStateFlow<List<String>>(emptyList())
    val chatLog: StateFlow<List<String>> = _chatLog.asStateFlow()
    private val _aiName = MutableStateFlow("Tiểu Trí")
    val aiName = _aiName.asStateFlow()
    private val _voiceType = MutableStateFlow("Dễ thương")
    val voiceType = _voiceType.asStateFlow()
    private val _personalityType = MutableStateFlow("Gần gũi")
    val personalityType = _personalityType.asStateFlow()
    private val _customPrompt = MutableStateFlow("")
    val customPrompt = _customPrompt.asStateFlow()
    private val _isWakeWordMode = MutableStateFlow(false)
    val isWakeWordMode = _isWakeWordMode.asStateFlow()
    private val _isHandsFreeMode = MutableStateFlow(false)
    val isHandsFreeMode = _isHandsFreeMode.asStateFlow()
    
    private val _partialSpeechText = MutableStateFlow("")
    val partialSpeechText = _partialSpeechText.asStateFlow()

    private var isWaitingForCommand = false

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var silenceTimer: kotlinx.coroutines.Job? = null

    private val apiKey = BuildConfig.GEMINI_API_KEY
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    init {
        tts = TextToSpeech(application, this)
        setupSpeechRecognizer(application)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("vi", "VN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Language not supported")
            }
        } else {
            Log.e("TTS", "Initialization failed")
        }
    }

    private fun setupSpeechRecognizer(context: Context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    _isListening.value = false
                    Log.e("Speech", "Error: $error")
                    // If in wake word mode and no manual command is being waited for, restart after silence
                    if (_isWakeWordMode.value && !isWaitingForCommand) {
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_CLIENT) {
                            startContinuousListening()
                        }
                    } else if (isWaitingForCommand) {
                        isWaitingForCommand = false
                        if (_isWakeWordMode.value) {
                            startContinuousListening()
                        }
                    }
                }
                override fun onResults(results: Bundle?) {
                    silenceTimer?.cancel()
                    _isListening.value = false
                    _partialSpeechText.value = ""
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        addLog("Bạn: $text")
                        
                        if (_isWakeWordMode.value && !isWaitingForCommand) {
                            val triggerName = _aiName.value.lowercase()
                            val lowerText = text.lowercase()
                            if (lowerText.contains(triggerName)) {
                                // Extract the part after the name
                                val commandPart = lowerText.substringAfter(triggerName).trim()
                                if (commandPart.isNotBlank()) {
                                    // User said name + command in the same sentence (e.g. "Tiểu Trí ơi thời tiết sao")
                                    isWaitingForCommand = false // reset flag since we are processing it
                                    processUserInput(commandPart)
                                } else {
                                    isWaitingForCommand = true
                                    speak("Dạ, em nghe đây")
                                }
                            } else {
                                // Name not detected, restart listening implicitly
                                startContinuousListening()
                            }
                        } else {
                            // Normal manual fetch or already waiting for command
                            isWaitingForCommand = false
                            processUserInput(text)
                        }
                    } else {
                        // Empty match
                        isWaitingForCommand = false
                        if (_isWakeWordMode.value) {
                            startContinuousListening()
                        }
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val currentText = matches[0]
                        if (currentText.isNotBlank()) {
                            _partialSpeechText.value = currentText
                            resetSilenceTimer()
                        }
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    fun startContinuousListening() {
        if (!_isWakeWordMode.value || isWaitingForCommand) return
        startListening()
    }

    fun startListening() {
        if (_isSpeaking.value) {
            tts?.stop()
            _isSpeaking.value = false
        }
        silenceTimer?.cancel()
        _partialSpeechText.value = ""
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Giảm độ trễ chờ im lặng để nhận diện nhanh hơn
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        _isListening.value = true
        _emotion.value = PetEmotion.LISTENING
        speechRecognizer?.startListening(intent)
        
        // Start an initial timer just in case no words are ever spoken
        resetSilenceTimer()
    }

    private fun resetSilenceTimer() {
        silenceTimer?.cancel()
        silenceTimer = viewModelScope.launch(Dispatchers.Main) {
            kotlinx.coroutines.delay(2000L) // Wait 2 seconds of silence
            if (_isListening.value) {
                stopListening()
            }
        }
    }

    fun stopListening() {
        silenceTimer?.cancel()
        speechRecognizer?.stopListening()
        _isListening.value = false
        isWaitingForCommand = false
    }

    fun pet() {
        _emotion.value = PetEmotion.HAPPY
        speak("Thích quá")
    }

    fun tickle() {
        _emotion.value = PetEmotion.EXCITED
        speak("Haha nhột quá")
    }

    fun onShake() {
        if (_isSpeaking.value && _emotion.value == PetEmotion.ERROR) return
        _emotion.value = PetEmotion.ERROR
        speak("Ối, chóng mặt quá chủ nhân ơi!")
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            if (_emotion.value == PetEmotion.ERROR) {
                _emotion.value = PetEmotion.IDLE
            }
        }
    }

    fun updateSettings(name: String, voice: String, personality: String, custom: String, wakeWordMode: Boolean = false, handsFreeMode: Boolean = false) {
        _aiName.value = name
        _voiceType.value = voice
        _personalityType.value = personality
        _customPrompt.value = custom
        _isWakeWordMode.value = wakeWordMode
        _isHandsFreeMode.value = handsFreeMode

        // Start or stop background listener on toggle change
        if (wakeWordMode) {
            startContinuousListening()
        } else {
            if (!isWaitingForCommand) stopListening()
        }

        when (voice) {
            "Dễ thương" -> {
                tts?.setPitch(1.5f)
                tts?.setSpeechRate(1.1f)
            }
            "Nam" -> {
                tts?.setPitch(0.5f)
                tts?.setSpeechRate(0.9f)
            }
            "Nữ" -> {
                tts?.setPitch(1.1f)
                tts?.setSpeechRate(1.0f)
            }
            "Robot" -> {
                tts?.setPitch(0.1f)
                tts?.setSpeechRate(0.8f)
            }
        }
    }

    private fun processUserInput(input: String) {
        if (input.trim().length <= 2) return
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (apiKey.isEmpty()) {
                    launch(Dispatchers.Main) {
                        _emotion.value = PetEmotion.SAD
                        val msg = "Chủ nhân quên nhập API Key trong phần Secrets rồi"
                        addLog("Hệ thống: $msg")
                        speak(msg)
                    }
                    return@launch
                }
                
                // Gemini API Call
                launch(Dispatchers.Main) { _emotion.value = PetEmotion.THINKING }
                val sysText = StringBuilder("Bạn là một thú cưng ảo robot tên ${_aiName.value}. Đang trò chuyện bằng tiếng Việt. Trả lời rất ngắn gọn (1-2 câu). ")
                if (_customPrompt.value.isNotEmpty()) {
                    sysText.append(_customPrompt.value).append(" ")
                }
                sysText.append("Tính cách của bạn: ${_personalityType.value}. ")
                sysText.append("Dạng JSON format: {\"response\": \"câu trả lời\", \"emotion\": \"idle|happy|surprised|sad|sleepy|love|excited\"}")

                val requestJson = JSONObject().apply {
                    put("system_instruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", sysText.toString())
                            })
                        })
                    })
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", input)
                                })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("response_mime_type", "application/json")
                    })
                }

                val body = requestJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    // MUST use preview model per gemini-api skill
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                    .post(body)
                    .build()

                var response: okhttp3.Response? = null
                var attempt = 0
                val maxAttempts = 3
                
                while (attempt < maxAttempts) {
                    response = httpClient.newCall(request).execute()
                    if (response.code == 429) {
                        // Rate limit exceeded, wait and retry
                        attempt++
                        if (attempt >= maxAttempts) break
                        kotlinx.coroutines.delay(2000L * attempt)
                    } else {
                        break
                    }
                }
                
                val finalResponse = response
                if (finalResponse != null && finalResponse.isSuccessful) {
                    val resStr = response.body?.string() ?: ""
                    val root = JSONObject(resStr)
                    val text = root.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")

                    // Parse internal JSON
                    val parsed = JSONObject(text)
                    val petResponse = parsed.optString("response", "Mình không hiểu lắm")
                    val petEmotion = parsed.optString("emotion", "idle")

                    launch(Dispatchers.Main) {
                        _emotion.value = when(petEmotion.lowercase()) {
                            "happy" -> PetEmotion.HAPPY
                            "sad" -> PetEmotion.SAD
                            "surprised" -> PetEmotion.SURPRISED
                            "sleepy" -> PetEmotion.SLEEPY
                            "love" -> PetEmotion.LOVE
                            "excited" -> PetEmotion.EXCITED
                            else -> PetEmotion.IDLE
                        }
                        addLog("Pet: $petResponse")
                        speak(petResponse)
                    }
                } else {
                    val code = finalResponse?.code ?: -1
                    val errorBody = finalResponse?.body?.string() ?: ""
                    Log.e("Gemini", "API Error: $code, $errorBody")
                    launch(Dispatchers.Main) {
                        _emotion.value = PetEmotion.SAD
                        val errorMsg = if (code == 429) "Mình đang phải nghĩ nhiều quá, chờ chút nhé!" else "Lỗi mạng rồi chủ nhân ơi. Mã lỗi: $code"
                        speak(errorMsg)
                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    _emotion.value = PetEmotion.SAD
                    Log.e("Gemini", "Error", e)
                }
            }
        }
    }

    private fun speak(text: String) {
        _isSpeaking.value = true
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_id")
        
        // Simple way to reset state (in real app, use UtteranceProgressListener)
        viewModelScope.launch {
            kotlinx.coroutines.delay((text.length * 100).toLong().coerceAtLeast(1000L))
            _isSpeaking.value = false
            if (_emotion.value == PetEmotion.EXCITED || _emotion.value == PetEmotion.HAPPY) {
                _emotion.value = PetEmotion.IDLE
            }
            
            // If we were waiting for command and the pet said "Dạ, em nghe đây", actually start listening
            if (isWaitingForCommand) {
                 startListening()
            } else {
                 if (_isHandsFreeMode.value) {
                     // Automatically listen for continuous conversation without wake word
                     isWaitingForCommand = true
                     startListening()
                 } else if (_isWakeWordMode.value) {
                     // Resume normal wake word mode
                     startContinuousListening()
                 }
            }
        }
    }

    private fun addLog(msg: String) {
        val current = _chatLog.value.toMutableList()
        current.add(msg)
        if (current.size > 10) current.removeAt(0)
        _chatLog.value = current
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }
}
