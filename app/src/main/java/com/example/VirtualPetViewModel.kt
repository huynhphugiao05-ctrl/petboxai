package com.example

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

enum class PetEmotion {
    IDLE, HAPPY, SAD, SURPRISED, EXCITED, LISTENING, LOVE, THINKING, ERROR, SLEEPY, SNEEZING, DIZZY, EATING
}

class VirtualPetViewModel(application: Application) : AndroidViewModel(application) {

    private val _emotion = MutableStateFlow(PetEmotion.IDLE)
    val emotion: StateFlow<PetEmotion> = _emotion.asStateFlow()

    // Giữ lại các StateFlow ảo để MainActivity biên dịch được nhưng không dùng tới
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _chatLog = MutableStateFlow<List<String>>(emptyList())
    val chatLog: StateFlow<List<String>> = _chatLog.asStateFlow()

    private val _partialSpeechText = MutableStateFlow("")
    val partialSpeechText: StateFlow<String> = _partialSpeechText.asStateFlow()

    private val _aiName = MutableStateFlow("Bot")
    val aiName: StateFlow<String> = _aiName.asStateFlow()

    private val _voiceType = MutableStateFlow("Robot")
    val voiceType: StateFlow<String> = _voiceType.asStateFlow()

    private val _personalityType = MutableStateFlow("Vô tri")
    val personalityType: StateFlow<String> = _personalityType.asStateFlow()

    private val _customPrompt = MutableStateFlow("")
    val customPrompt: StateFlow<String> = _customPrompt.asStateFlow()

    private val _isWakeWordMode = MutableStateFlow(false)
    val isWakeWordMode: StateFlow<Boolean> = _isWakeWordMode.asStateFlow()

    private val _isHandsFreeMode = MutableStateFlow(false)
    val isHandsFreeMode: StateFlow<Boolean> = _isHandsFreeMode.asStateFlow()

    private var behaviorJob: kotlinx.coroutines.Job? = null
    private var lastInteractionTime = System.currentTimeMillis()
    
    private var hasAnnouncedMorning = false
    private var hasAnnouncedNight = false
    private var isMealTimeAnnounced = false

    private var tts: TextToSpeech? = null

    init {
        // Khởi tạo TextToSpeech cục bộ tạo âm thanh minion/robot siêu kute
        tts = TextToSpeech(application) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("vi", "VN")
                tts?.setPitch(2.0f) // Giọng siêu cao thẻo như minion
                tts?.setSpeechRate(1.6f) // Nói siêu nhanh líu lo
            }
        }
        startIdleBehavior()
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
    }

    private fun playSound(texts: List<String>) {
        tts?.speak(texts.random(), TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun startIdleBehavior() {
        behaviorJob?.cancel()
        behaviorJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val currentTime = System.currentTimeMillis()
                
                val calendar = Calendar.getInstance()
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)
                val isNight = hour >= 21 || hour < 6 // Lùi giờ ngủ xuống 21h
                
                // Routine Announcer Logic
                if (hour == 6 && minute == 0 && !hasAnnouncedMorning) {
                    hasAnnouncedMorning = true
                    hasAnnouncedNight = false // reset for night
                    _emotion.value = PetEmotion.EXCITED
                    tts?.speak("Ò ó o... 6 giờ sáng rồi, dậy đi mọi người ơi, vươn vai chào ngày mới nào!", TextToSpeech.QUEUE_FLUSH, null, null)
                    markInteraction()
                    restartBehaviorTimer(10000)
                    continue
                }
                
                if (hour == 21 && minute == 0 && !hasAnnouncedNight) {
                    hasAnnouncedNight = true
                    hasAnnouncedMorning = false // reset for morning
                    _emotion.value = PetEmotion.SLEEPY
                    tts?.speak("9 giờ tối rồi, các bạn đi ngủ sớm cho khỏe nha. Nhớ đắp mền kẻo lạnh xì trum, chúc ngủ ngon!", TextToSpeech.QUEUE_FLUSH, null, null)
                    markInteraction()
                    restartBehaviorTimer(10000)
                    continue
                }

                // Giờ ăn (7h, 12h, 19h)
                val isMealTime = (hour == 7 || hour == 12 || hour == 19)
                if (isMealTime && minute == 0 && !isMealTimeAnnounced) {
                    isMealTimeAnnounced = true
                    _emotion.value = PetEmotion.EATING
                    tts?.speak("Đến giờ ăn rồi! Măm măm măm, chóp chép ngon quá!", TextToSpeech.QUEUE_FLUSH, null, null)
                    markInteraction()
                    restartBehaviorTimer(15000)
                    continue
                }
                if (!isMealTime && minute > 0) {
                    isMealTimeAnnounced = false
                }

                // Trạng thái vô tri: Nếu 5 giây không ai đụng vào
                if (currentTime - lastInteractionTime > 5000) {
                    if (isNight) {
                        if (_emotion.value != PetEmotion.SLEEPY) {
                            _emotion.value = PetEmotion.SLEEPY
                        }
                        if (Math.random() > 0.95) {
                            tts?.setPitch(0.5f) // Ngáy trầm
                            playSound(listOf("Khò... khò...", "Zzz zzz..."))
                            tts?.setPitch(2.0f) 
                        }
                    } else {
                        // Ban ngày thi thoảng vô tri
                        if (Math.random() > 0.8) {
                            var nextEmotion = listOf(
                                PetEmotion.IDLE, PetEmotion.IDLE, PetEmotion.HAPPY, 
                                PetEmotion.THINKING, PetEmotion.IDLE
                            ).random()

                            // 5% tỷ lệ hắt xì ngẫu nhiên
                            if (Math.random() > 0.95) {
                                nextEmotion = PetEmotion.SNEEZING
                                playSound(listOf("Hắt xì... ui ướt hết màn hình!", "Ắt xì", "Sụt sịt... lạnh quá"))
                                _emotion.value = nextEmotion
                                restartBehaviorTimer(3000)
                                continue
                            }

                            _emotion.value = nextEmotion

                            if (Math.random() > 0.9) {
                                val sillySounds = when (nextEmotion) {
                                    PetEmotion.HAPPY -> listOf("La la la", "Vê lốc đê", "Tê ga tê ga", "Ha ha ha")
                                    PetEmotion.THINKING -> listOf("Ủa...", "Hừm...", "Khó hiểu ghê", "Nà ní")
                                    else -> listOf("Bíp bíp bíp", "Xì chui", "Tư tút", "Ngáp ngáp")
                                }
                                playSound(sillySounds)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun markInteraction() {
        lastInteractionTime = System.currentTimeMillis()
    }

    fun onShake() {
        markInteraction()
        _emotion.value = PetEmotion.DIZZY // Lắc mạnh thì DIZZY thay vì chỉ Surprised
        playSound(listOf("Chóng mặt quá... chóng mặt quá", "Ối giời ôi", "Động đất à"))
        restartBehaviorTimer()
    }

    fun pet() { // vuốt ve / quẹt
        markInteraction()
        _emotion.value = PetEmotion.EXCITED
        playSound(listOf("Meo meo", "Đỉnh chóp", "Khoái khoái", "Tuyệt vời"))
        restartBehaviorTimer()
    }
    
    fun poke() { // chạm vào
        markInteraction()
        _emotion.value = PetEmotion.LOVE
        playSound(listOf("Á á", "Nhột quá đi", "Thích bạn nhứt", "Trái tim", "Chụt chụt"))
        restartBehaviorTimer()
    }

    fun doubleTap() { // gõ 2 lần để ăn
        markInteraction()
        _emotion.value = PetEmotion.EATING
        playSound(listOf("Măm măm", "Ngon tuyệt cú mèo", "Chóp chép chóp chép"))
        restartBehaviorTimer(5000)
    }

    private fun restartBehaviorTimer(delayMs: Long = 3000) {
        viewModelScope.launch {
            delay(delayMs)
            if (System.currentTimeMillis() - lastInteractionTime >= delayMs) {
                _emotion.value = PetEmotion.IDLE
            }
        }
    }

    fun startListening() { poke() } 
    fun stopListening() {}
    fun updateSettings(n: String, v: String, p: String, c: String, w: Boolean, h: Boolean) {}
}
