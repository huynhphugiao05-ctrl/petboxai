package com.example

import android.app.Application
import android.content.Context
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
    IDLE, HAPPY, SAD, SURPRISED, EXCITED, LISTENING, LOVE, THINKING, ERROR, SLEEPY, SNEEZING, DIZZY, EATING, EAVESDROPPING, PLAYING_PHONE, DANCING
}

class VirtualPetViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("PetbotSettings", Context.MODE_PRIVATE)

    private val _emotion = MutableStateFlow(PetEmotion.IDLE)
    val emotion: StateFlow<PetEmotion> = _emotion.asStateFlow()
    
    private val _sleepHour = MutableStateFlow(prefs.getInt("sleepHour", 22))
    val sleepHour: StateFlow<Int> = _sleepHour.asStateFlow()

    private val _wakeHour = MutableStateFlow(prefs.getInt("wakeHour", 6))
    val wakeHour: StateFlow<Int> = _wakeHour.asStateFlow()

    private val _customReminder = MutableStateFlow(prefs.getString("customReminder", "Vươn vai chào ngày mới nào!") ?: "")
    val customReminder: StateFlow<String> = _customReminder.asStateFlow()

    private val _petName = MutableStateFlow(prefs.getString("petName", "Bé Pet") ?: "Bé Pet")
    val petName: StateFlow<String> = _petName.asStateFlow()

    private val _isMuteIdleSounds = MutableStateFlow(prefs.getBoolean("isMuteIdleSounds", false))
    val isMuteIdleSounds: StateFlow<Boolean> = _isMuteIdleSounds.asStateFlow()

    private val _isDeepNightMode = MutableStateFlow(false)
    val isDeepNightMode: StateFlow<Boolean> = _isDeepNightMode.asStateFlow()

    // Cũ
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()
    private val _chatLog = MutableStateFlow<List<String>>(emptyList())
    val chatLog: StateFlow<List<String>> = _chatLog.asStateFlow()

    private var behaviorJob: kotlinx.coroutines.Job? = null
    private var lastInteractionTime = System.currentTimeMillis()
    
    private var hasAnnouncedMorning = false
    private var hasAnnouncedNight = false
    private var isMealTimeAnnounced = false

    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(application) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("vi", "VN")
                tts?.setPitch(2.0f)
                tts?.setSpeechRate(1.6f)
            }
        }
        startIdleBehavior()
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
    }

    fun saveConfig(sleepH: Int, wakeH: Int, reminder: String, name: String, mute: Boolean) {
        prefs.edit().apply {
            putInt("sleepHour", sleepH)
            putInt("wakeHour", wakeH)
            putString("customReminder", reminder)
            putString("petName", name)
            putBoolean("isMuteIdleSounds", mute)
        }.apply()
        
        _sleepHour.value = sleepH
        _wakeHour.value = wakeH
        _customReminder.value = reminder
        _petName.value = name
        _isMuteIdleSounds.value = mute
        
        // Reset announcement flags on save if needed
        hasAnnouncedMorning = false
        hasAnnouncedNight = false
    }

    private fun playSound(texts: List<String>) {
        if (!_isMuteIdleSounds.value) {
            tts?.speak(texts.random(), TextToSpeech.QUEUE_FLUSH, null, null)
        }
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
                
                val sHour = _sleepHour.value
                val wHour = _wakeHour.value
                val isNight = if (sHour > wHour) {
                    hour >= sHour || hour < wHour
                } else {
                    hour in sHour until wHour
                }
                
                _isDeepNightMode.value = isNight

                // Routine Announcer Logic
                if (hour == wHour && minute == 0 && !hasAnnouncedMorning) {
                    hasAnnouncedMorning = true
                    hasAnnouncedNight = false
                    _emotion.value = PetEmotion.EXCITED
                    // Lời báo thức k bắt buộc bị mute
                    val n = _petName.value
                    val rm = _customReminder.value
                    tts?.speak("Ò ó o... tới giờ rồi, mình là $n đây! $rm", TextToSpeech.QUEUE_FLUSH, null, null)
                    markInteraction()
                    restartBehaviorTimer(10000)
                    continue
                }
                
                if (hour == sHour && minute == 0 && !hasAnnouncedNight) {
                    hasAnnouncedNight = true
                    hasAnnouncedMorning = false
                    _emotion.value = PetEmotion.SLEEPY
                    val n = _petName.value
                    tts?.speak("$sHour giờ tối rồi, đi ngủ sớm cho khỏe nha. Nhớ đắp mền kẻo lạnh xì trum, $n chúc bạn ngủ ngon!", TextToSpeech.QUEUE_FLUSH, null, null)
                    markInteraction()
                    restartBehaviorTimer(10000)
                    continue
                }

                if (!isNight) {
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
                }

                // Trạng thái vô tri: Nếu 5 giây không ai đụng vào
                if (currentTime - lastInteractionTime > 5000) {
                    if (isNight) {
                        if (_emotion.value != PetEmotion.SLEEPY) {
                            _emotion.value = PetEmotion.SLEEPY
                        }
                    } else {
                        // Ban ngày thi thoảng vô tri
                        if (Math.random() > 0.8) {
                            var nextEmotion = listOf(
                                PetEmotion.IDLE, PetEmotion.IDLE, PetEmotion.HAPPY, 
                                PetEmotion.THINKING, PetEmotion.IDLE
                            ).random()

                            val randVal = Math.random()
                            if (randVal > 0.96) {
                                nextEmotion = PetEmotion.SNEEZING
                                playSound(listOf("Hắt xì... ui ướt hết màn hình!", "Ắt xì", "Sụt sịt... lạnh quá"))
                                _emotion.value = nextEmotion
                                restartBehaviorTimer(3000)
                                continue
                            } else if (randVal > 0.92) {
                                nextEmotion = PetEmotion.DANCING
                                playSound(listOf("Ít sà Vinahao!", "Lên nóc nhà là bắt con gà!", "Tùng tùng chát chát... quẩy lên"))
                                _emotion.value = nextEmotion
                                restartBehaviorTimer(5000)
                                continue
                            } else if (randVal > 0.88) {
                                nextEmotion = PetEmotion.EAVESDROPPING
                                _emotion.value = nextEmotion
                                // Cho cái tai ngoe nguẩy 1 giây mới bắt đầu thì thào
                                delay(1000)
                                playSound(listOf("Có biến gì thế...", "Đang nói xấu mình hở...", "Ai kêu tui đó..."))
                                restartBehaviorTimer(4000)
                                continue
                            } else if (randVal > 0.84) {
                                nextEmotion = PetEmotion.PLAYING_PHONE
                                playSound(listOf("Lướt tóp tóp... hehe", "Video này hài xỉu", "Xem cờ nhíp tí"))
                                _emotion.value = nextEmotion
                                restartBehaviorTimer(6000)
                                continue
                            }

                            _emotion.value = nextEmotion

                            if (Math.random() > 0.8) {
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
        if (_isDeepNightMode.value) return
        markInteraction()
        _emotion.value = PetEmotion.DIZZY
        playSound(listOf("Chóng mặt quá... chóng mặt quá", "Ối giời ôi", "Động đất à"))
        restartBehaviorTimer()
    }

    fun pet() {
        if (_isDeepNightMode.value) return
        markInteraction()
        _emotion.value = PetEmotion.EXCITED
        playSound(listOf("Meo meo", "Đỉnh chóp", "Khoái khoái", "Tuyệt vời"))
        restartBehaviorTimer()
    }
    
    fun poke() {
        if (_isDeepNightMode.value) return
        markInteraction()
        _emotion.value = PetEmotion.LOVE
        playSound(listOf("Á á", "Nhột quá đi", "Thích bạn nhứt", "Trái tim", "Chụt chụt"))
        restartBehaviorTimer()
    }

    fun doubleTap() {
        if (_isDeepNightMode.value) return
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
}
