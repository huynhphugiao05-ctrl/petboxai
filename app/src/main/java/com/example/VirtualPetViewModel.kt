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
    IDLE, HAPPY, SAD, SURPRISED, EXCITED, LISTENING, LOVE, THINKING, ERROR, SLEEPY
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
        // Random chọn ra 1 câu vô tri để phát âm
        tts?.speak(texts.random(), TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun startIdleBehavior() {
        behaviorJob?.cancel()
        behaviorJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val currentTime = System.currentTimeMillis()
                
                // Trạng thái vô tri: Nếu 5 giây không ai đụng vào
                if (currentTime - lastInteractionTime > 5000) {
                    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    val isNight = hour >= 23 || hour <= 6
                    
                    if (isNight) {
                        // Ban đêm thì khò khò
                        if (_emotion.value != PetEmotion.SLEEPY) {
                            _emotion.value = PetEmotion.SLEEPY
                        }
                        // Thỉnh thoảng nghe ngáy khò khò nhỏ nhỏ
                        if (Math.random() > 0.95) {
                            tts?.setPitch(0.5f) // Ngáy trầm
                            playSound(listOf("Khò... khò...", "Zzz zzz...", "Khò..."))
                            tts?.setPitch(2.0f) // Trả lại giọng minion
                        }
                    } else {
                        // Ban ngày thi thoảng vô tri thay đổi sắc mặt
                        // Tỉ lệ 20% mỗi giây sẽ thay đổi cảm xúc
                        if (Math.random() > 0.8) {
                            val randomEmotions = listOf(
                                PetEmotion.IDLE, PetEmotion.IDLE, PetEmotion.HAPPY, 
                                PetEmotion.THINKING, PetEmotion.IDLE
                            )
                            val nextEmotion = randomEmotions.random()
                            _emotion.value = nextEmotion

                            // 10% tỷ lệ lảm nhảm ra tiếng khi vô tri
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

    // Các hàm tương tác
    fun onShake() {
        markInteraction()
        _emotion.value = PetEmotion.SURPRISED
        playSound(listOf("Ối giời ôi", "Động đất à", "Chóng mặt quá", "A lô a lô!"))
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

    private fun restartBehaviorTimer() {
        // Sau khi bị tương tác, nó giữ mặt 3 giây rồi trở lại bình thường
        viewModelScope.launch {
            delay(3000)
            if (System.currentTimeMillis() - lastInteractionTime >= 3000) {
                _emotion.value = PetEmotion.IDLE
            }
        }
    }

    // Giữ lại các hàm rỗng để MainActivity hoạt động
    fun startListening() { poke() } // Chạm vào màn hình sẽ gọi poke
    fun stopListening() {}
    fun updateSettings(n: String, v: String, p: String, c: String, w: Boolean, h: Boolean) {}
}
