package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

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

    init {
        startIdleBehavior()
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
                    } else {
                        // Ban ngày thi thoảng vô tri thay đổi sắc mặt
                        // Tỉ lệ 20% mỗi giây sẽ thay đổi cảm xúc
                        if (Math.random() > 0.8) {
                            val randomEmotions = listOf(
                                PetEmotion.IDLE, PetEmotion.IDLE, PetEmotion.HAPPY, 
                                PetEmotion.THINKING, PetEmotion.IDLE
                            )
                            _emotion.value = randomEmotions.random()
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
        restartBehaviorTimer()
    }

    fun pet() { // vuốt ve
        markInteraction()
        _emotion.value = PetEmotion.EXCITED
        restartBehaviorTimer()
    }
    
    fun poke() { // chạm vào
        markInteraction()
        _emotion.value = PetEmotion.LOVE
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
    fun startListening() { poke() } // Chạm vào màn hình sẽ gọi startListening
    fun stopListening() {}
    fun updateSettings(n: String, v: String, p: String, c: String, w: Boolean, h: Boolean) {}
}
