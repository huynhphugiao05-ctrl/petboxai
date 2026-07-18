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
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import java.io.FileOutputStream

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

    private var llmInference: LlmInference? = null
    private var isModelLoaded = false

    init {
        tts = TextToSpeech(application, this)
        setupSpeechRecognizer(application)
        loadModel(application)
    }

    private fun loadModel(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val modelName = "gemma-2b-it.task"
            val modelFile = File(context.filesDir, modelName)
            
            if (!modelFile.exists()) {
                launch(Dispatchers.Main) {
                    _emotion.value = PetEmotion.SAD
                    addLog("Hệ thống: Chưa tìm thấy não AI!")
                    addLog("Vui lòng cắm cáp USB và chép file gemma-2b-it.task vào thư mục sau trên điện thoại: Android/data/com.aistudio.xiaozhiclient.rpnxqa/files/")
                    speak("Chủ nhân ơi, em chưa có não. Hãy kết nối điện thoại với máy tính để truyền não vào cho em nhé!")
                }
                return@launch
            }
            
            try {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(128)
                    .build()
                llmInference = LlmInference.createFromOptions(context, options)
                isModelLoaded = true
                launch(Dispatchers.Main) { 
                    addLog("Hệ thống: Bộ não offline đã sẵn sàng!") 
                    _emotion.value = PetEmotion.HAPPY
                    speak("Em đã khởi động xong não bộ, hoàn toàn offline ạ!")
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) { addLog("Hệ thống: Lỗi khởi tạo MediaPipe: ${e.message}") }
            }
        }
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
                if (!isModelLoaded || llmInference == null) {
                    launch(Dispatchers.Main) {
                        _emotion.value = PetEmotion.SAD
                        val msg = "Bộ não chưa tải xong hoặc có lỗi khởi tạo, vui lòng đợi thêm!"
                        addLog("Hệ thống: $msg")
                        speak(msg)
                    }
                    return@launch
                }
                
                launch(Dispatchers.Main) { _emotion.value = PetEmotion.THINKING }
                
                val promptText = java.lang.StringBuilder()
                promptText.append("Bạn là một thú cưng ảo tên ${_aiName.value}. ")
                if (_customPrompt.value.isNotEmpty()) {
                    promptText.append("${_customPrompt.value} ")
                }
                promptText.append("Tính cách của bạn: ${_personalityType.value}. ")
                promptText.append("Bắt buộc chỉ trả lời bằng Tiếng Việt và rất ngắn gọn trong 1-2 câu. Không dùng ký tự lạ hay format đặc biệt.\n\n")
                promptText.append("Chủ nhân: $input\n")
                promptText.append("${_aiName.value}:")

                val resStr = llmInference?.generateResponse(promptText.toString()) ?: ""
                val petResponse = resStr.trim()
                
                // Do mô hình thô trên máy sinh JSON độ tin cậy thấp, ta dùng từ khoá cảm xúc
                var petEmotion = PetEmotion.IDLE
                val lowerRes = petResponse.lowercase()
                if (lowerRes.contains("vui") || lowerRes.contains("haha") || lowerRes.contains("dạ") || lowerRes.contains("thích")) petEmotion = PetEmotion.HAPPY
                else if (lowerRes.contains("buồn") || lowerRes.contains("tội") || lowerRes.contains("khóc")) petEmotion = PetEmotion.SAD
                else if (lowerRes.contains("ngạc nhiên") || lowerRes.contains("ủa") || lowerRes.contains("trời ơi")) petEmotion = PetEmotion.SURPRISED
                else if (lowerRes.contains("ngủ") || lowerRes.contains("buồn ngủ") || lowerRes.contains("ngáp")) petEmotion = PetEmotion.SLEEPY
                else if (lowerRes.contains("yêu") || lowerRes.contains("thương") || lowerRes.contains("ôm")) petEmotion = PetEmotion.LOVE
                else if (lowerRes.contains("tức") || lowerRes.contains("giận") || lowerRes.contains("ghét") || lowerRes.contains("đau")) petEmotion = PetEmotion.ERROR

                launch(Dispatchers.Main) {
                    _emotion.value = petEmotion
                    addLog("Pet: $petResponse")
                    speak(petResponse)
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    _emotion.value = PetEmotion.SAD
                    Log.e("Gemma", "Error", e)
                    speak("Đầu em đau quá, không nghĩ được gì cả.")
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
