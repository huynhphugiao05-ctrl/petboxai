package com.example

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import android.view.WindowManager
import kotlinx.coroutines.delay
import com.example.ui.theme.MyApplicationTheme
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.Context
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    private val viewModel: VirtualPetViewModel by viewModels()

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var acceleration = 0f
    private var currentAcceleration = SensorManager.GRAVITY_EARTH
    private var lastAcceleration = SensorManager.GRAVITY_EARTH

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            lastAcceleration = currentAcceleration
            currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta: Float = currentAcceleration - lastAcceleration
            acceleration = acceleration * 0.9f + delta
            
            // Shake threshold
            if (acceleration > 12) {
                viewModel.onShake()
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startListening()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Keep screen on 24/7
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        enableEdgeToEdge()
        
        // Hide system bars for full screen immersive mode
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    VirtualPetScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = viewModel,
                        onStartListening = {
                            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                viewModel.startListening()
                            } else {
                                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager?.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(sensorListener)
    }
}

@Composable
fun VirtualPetScreen(
    modifier: Modifier = Modifier, 
    viewModel: VirtualPetViewModel,
    onStartListening: () -> Unit
) {
    val emotion by viewModel.emotion.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val chatLog by viewModel.chatLog.collectAsState()

    val bg = Color(0xFF161A23)
    val lightText = Color(0xFFE2E8F0)
    var showSettings by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().background(bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                AdvancedRobot(
                    emotion = emotion,
                    isSpeaking = isSpeaking,
                    isListening = isListening,
                    onTap = {
                        if (isListening) viewModel.stopListening()
                        else onStartListening()
                    },
                    onPet = { viewModel.pet() }
                )
            }
            
            val latestMessage = chatLog.lastOrNull()
            if (latestMessage != null) {
                Text(
                    text = latestMessage,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Medium,
                        color = lightText
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                Text(
                    text = "Chạm vào bé để bắt đầu",
                    style = MaterialTheme.typography.titleMedium,
                    color = lightText.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }

        IconButton(
            onClick = { showSettings = true },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = lightText
            )
        }

        if (showSettings) {
            val currentVoice by viewModel.voiceType.collectAsState()
            val currentPersonality by viewModel.personalityType.collectAsState()
            val currentCustomPrompt by viewModel.customPrompt.collectAsState()
            
            SettingsDialog(
                initialVoice = currentVoice,
                initialPersonality = currentPersonality,
                initialCustomPrompt = currentCustomPrompt,
                onDismiss = { showSettings = false },
                onSave = { voice, personality, custom ->
                    viewModel.updateSettings(voice, personality, custom)
                    showSettings = false
                }
            )
        }
    }
}

@Composable
fun SettingsDialog(
    initialVoice: String,
    initialPersonality: String,
    initialCustomPrompt: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF232A3B),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Cài đặt AI",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                // Voice options
                Text("Giọng nói", color = Color.LightGray, style = MaterialTheme.typography.labelLarge)
                var selectedVoice by remember { mutableStateOf(initialVoice) }
                val voices = listOf("Dễ thương", "Nam", "Nữ")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    voices.forEach { voice ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (selectedVoice == voice) Color(0xFFFF6B9D) else Color(0xFF333D52),
                            modifier = Modifier.clickable { selectedVoice = voice }
                        ) {
                            Text(voice, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                }
                
                // Personality options
                Text("Tính cách", color = Color.LightGray, style = MaterialTheme.typography.labelLarge)
                var selectedPersonality by remember { mutableStateOf(initialPersonality) }
                val personalities = listOf("Lịch sự", "Hài hước", "Gần gũi", "Chợ búa")
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    personalities.forEach { p ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (selectedPersonality == p) Color(0xFFFF6B9D) else Color(0xFF333D52),
                            modifier = Modifier.clickable { selectedPersonality = p }
                        ) {
                            Text(p, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                }

                // Custom prompt / personal info
                Text("Cá nhân hóa", color = Color.LightGray, style = MaterialTheme.typography.labelLarge)
                var customPrompt by remember { mutableStateOf(initialCustomPrompt) }
                OutlinedTextField(
                    value = customPrompt,
                    onValueChange = { customPrompt = it },
                    placeholder = { Text("Ví dụ: Bạn là trợ lý ảo thích ăn gà rán...", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFF6B9D),
                        unfocusedBorderColor = Color(0xFF333D52),
                        cursorColor = Color(0xFFFF6B9D)
                    ),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onSave(selectedVoice, selectedPersonality, customPrompt) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B9D)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Lưu & Đóng", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun AdvancedRobot(
    emotion: PetEmotion,
    isSpeaking: Boolean,
    isListening: Boolean,
    onTap: () -> Unit,
    onPet: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    
    // Breathing animation (vertical float)
    val floatAnim by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    // Heart pulse
    val heartPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (emotion == PetEmotion.LOVE) 1.3f else if (isSpeaking) 1.15f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (emotion == PetEmotion.LOVE) 600 else 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Blinking logic
    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(emotion) {
        while (true) {
            delay((2000..6000).random().toLong())
            if (emotion != PetEmotion.SLEEPY) {
                isBlinking = true
                delay(150)
                isBlinking = false
                // Double blink occasionally
                if (Math.random() > 0.7) {
                    delay(100)
                    isBlinking = true
                    delay(150)
                    isBlinking = false
                }
            }
        }
    }
    
    // Eye movement logic
    var lookOffset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(emotion) {
        while(true) {
            delay((2000..5000).random().toLong())
            if (emotion == PetEmotion.IDLE) {
                val dx = (-15..15).random().toFloat()
                val dy = (-8..8).random().toFloat()
                lookOffset = Offset(dx, dy)
                delay((1000..2000).random().toLong())
                lookOffset = Offset.Zero
            }
        }
    }

    val bodyColor = Color(0xFFFAF5ED)
    val earColor = Color(0xFFFFB2B2)
    
    // Target ear rotation
    val targetEarRot = when (emotion) {
        PetEmotion.SAD -> -15f
        PetEmotion.LISTENING -> 15f
        PetEmotion.SURPRISED -> 20f
        else -> 0f
    }
    val earRot by animateFloatAsState(targetValue = targetEarRot, animationSpec = tween(500))

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(340.dp)
            .offset(y = floatAnim.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            }
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    if (kotlin.math.abs(dragAmount.x) > 10 || kotlin.math.abs(dragAmount.y) > 10) {
                        onPet()
                    }
                }
            }
    ) {
        // Draw Ears (left and right)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val earW = 44.dp.toPx()
            val earH = 110.dp.toPx()
            
            val earOffset = 24.dp.toPx()
            
            // Left ear
            withTransform({
                translate(left = earOffset, top = h * 0.35f)
                rotate(degrees = -earRot, pivot = Offset(earW, earH/2))
            }) {
                drawRoundRect(
                    color = earColor,
                    size = Size(earW, earH),
                    cornerRadius = CornerRadius(earW/2, earW/2)
                )
            }
            
            // Right ear
            withTransform({
                translate(left = w - earOffset - earW, top = h * 0.35f)
                rotate(degrees = earRot, pivot = Offset(0f, earH/2))
            }) {
                drawRoundRect(
                    color = earColor,
                    size = Size(earW, earH),
                    cornerRadius = CornerRadius(earW/2, earW/2)
                )
            }
        }

        // Draw Body (Cream White)
        Surface(
            modifier = Modifier.size(width = 280.dp, height = 300.dp),
            shape = RoundedCornerShape(80.dp),
            color = bodyColor,
            shadowElevation = 24.dp,
            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFF0E5D8))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                // Face Screen
                Box(
                    modifier = Modifier
                        .size(width = 248.dp, height = 200.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF2A2A35), Color(0xFF0D0D12)),
                                radius = 500f
                            ),
                            shape = RoundedCornerShape(70.dp)
                        )
                        .border(4.dp, Color(0xFF050505), RoundedCornerShape(70.dp))
                ) {
                    RobotFaceCanvas(
                        emotion = emotion,
                        isSpeaking = isSpeaking,
                        isBlinking = isBlinking,
                        lookOffset = lookOffset
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Heart
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Heart",
                    tint = Color(0xFFFF6B9D),
                    modifier = Modifier
                        .size(44.dp)
                        .scale(heartPulse)
                )
                
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
        
        // Listening / Speaking Status Indicator over robot
        if (isListening || isSpeaking) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFF232A3B),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-30).dp)
                    .height(32.dp),
                shadowElevation = 8.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (isListening) Color(0xFFFF6B9D) else Color(0xFF6BFF9D), 
                                shape = CircleShape
                            )
                    )
                    Text(
                        text = if (isListening) "Đang nghe..." else "Đang trả lời...",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFE2E8F0)
                    )
                }
            }
        }
    }
}

@Composable
fun RobotFaceCanvas(
    emotion: PetEmotion,
    isSpeaking: Boolean,
    isBlinking: Boolean,
    lookOffset: Offset
) {
    val featureColor = Color.White
    val cheekColor = Color(0xFFFF8B94)
    val pupilColor = Color(0xFF222222)
    
    // Smooth animate look offset
    val animLookX by animateFloatAsState(targetValue = lookOffset.x, animationSpec = tween(300))
    val animLookY by animateFloatAsState(targetValue = lookOffset.y, animationSpec = tween(300))

    val infiniteTransition = rememberInfiniteTransition()
    val speakMouthAnim by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(
            animation = tween(150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        
        // Cheeks
        val cheekY = h * 0.65f
        val cheekRx = 18.dp.toPx()
        val cheekRy = 10.dp.toPx()
        val lx = w * 0.25f
        val rx = w * 0.75f
        
        if (emotion != PetEmotion.SLEEPY) {
            val cheekAlpha = if (emotion == PetEmotion.HAPPY || emotion == PetEmotion.LOVE || emotion == PetEmotion.EXCITED) 1f else 0.4f
            drawOval(
                color = cheekColor.copy(alpha = cheekAlpha),
                topLeft = Offset(lx - cheekRx, cheekY - cheekRy),
                size = Size(cheekRx * 2, cheekRy * 2)
            )
            drawOval(
                color = cheekColor.copy(alpha = cheekAlpha),
                topLeft = Offset(rx - cheekRx, cheekY - cheekRy),
                size = Size(cheekRx * 2, cheekRy * 2)
            )
        }

        // Eyes
        val eyeY = h * 0.45f
        val eyeR = 26.dp.toPx()
        val strokeW = 10.dp.toPx()

        if (isBlinking && emotion != PetEmotion.SLEEPY) {
            drawLine(featureColor, Offset(lx - eyeR, eyeY), Offset(lx + eyeR, eyeY), strokeW, StrokeCap.Round)
            drawLine(featureColor, Offset(rx - eyeR, eyeY), Offset(rx + eyeR, eyeY), strokeW, StrokeCap.Round)
        } else {
            when (emotion) {
                PetEmotion.HAPPY, PetEmotion.EXCITED -> {
                    drawArc(featureColor, 180f, 180f, false, Offset(lx - eyeR, eyeY - eyeR), Size(eyeR*2, eyeR*2), style = Stroke(strokeW, cap = StrokeCap.Round))
                    drawArc(featureColor, 180f, 180f, false, Offset(rx - eyeR, eyeY - eyeR), Size(eyeR*2, eyeR*2), style = Stroke(strokeW, cap = StrokeCap.Round))
                }
                PetEmotion.SAD -> {
                    drawLine(featureColor, Offset(lx - eyeR, eyeY - eyeR/4), Offset(lx + eyeR, eyeY + eyeR/2), strokeW, StrokeCap.Round)
                    drawLine(featureColor, Offset(rx - eyeR, eyeY + eyeR/2), Offset(rx + eyeR, eyeY - eyeR/4), strokeW, StrokeCap.Round)
                }
                PetEmotion.SLEEPY -> {
                    drawLine(featureColor, Offset(lx - eyeR, eyeY), Offset(lx + eyeR, eyeY), strokeW, StrokeCap.Round)
                    drawLine(featureColor, Offset(rx - eyeR, eyeY), Offset(rx + eyeR, eyeY), strokeW, StrokeCap.Round)
                    // Zzz
                    drawPath(Path().apply {
                        moveTo(lx + eyeR + 10.dp.toPx(), eyeY - 40.dp.toPx())
                        lineTo(lx + eyeR + 30.dp.toPx(), eyeY - 40.dp.toPx())
                        lineTo(lx + eyeR + 10.dp.toPx(), eyeY - 20.dp.toPx())
                        lineTo(lx + eyeR + 30.dp.toPx(), eyeY - 20.dp.toPx())
                    }, featureColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                }
                PetEmotion.SURPRISED -> {
                    drawCircle(featureColor, eyeR * 1.2f, Offset(lx, eyeY))
                    drawCircle(featureColor, eyeR * 1.2f, Offset(rx, eyeY))
                    // Pupils
                    drawCircle(pupilColor, eyeR * 0.4f, Offset(lx, eyeY))
                    drawCircle(pupilColor, eyeR * 0.4f, Offset(rx, eyeY))
                }
                PetEmotion.LOVE -> {
                    // Hearts
                    val drawHeart = { center: Offset, radius: Float ->
                        val path = Path().apply {
                            val r = radius * 0.8f
                            moveTo(center.x, center.y + r)
                            cubicTo(center.x + r * 1.5f, center.y - r * 0.5f, center.x + r, center.y - r * 1.5f, center.x, center.y - r * 0.5f)
                            cubicTo(center.x - r, center.y - r * 1.5f, center.x - r * 1.5f, center.y - r * 0.5f, center.x, center.y + r)
                        }
                        drawPath(path, cheekColor)
                    }
                    drawHeart(Offset(lx, eyeY), eyeR * 1.2f)
                    drawHeart(Offset(rx, eyeY), eyeR * 1.2f)
                }
                PetEmotion.THINKING -> {
                    drawCircle(featureColor, eyeR, Offset(lx, eyeY))
                    drawCircle(featureColor, eyeR, Offset(rx, eyeY))
                    drawCircle(pupilColor, eyeR * 0.5f, Offset(lx + 10.dp.toPx(), eyeY - 10.dp.toPx()))
                    drawCircle(pupilColor, eyeR * 0.5f, Offset(rx + 10.dp.toPx(), eyeY - 10.dp.toPx()))
                }
                PetEmotion.ERROR -> {
                    // Dizzy X eyes
                    drawLine(featureColor, Offset(lx - eyeR, eyeY - eyeR), Offset(lx + eyeR, eyeY + eyeR), strokeW, StrokeCap.Round)
                    drawLine(featureColor, Offset(lx - eyeR, eyeY + eyeR), Offset(lx + eyeR, eyeY - eyeR), strokeW, StrokeCap.Round)
                    
                    drawLine(featureColor, Offset(rx - eyeR, eyeY - eyeR), Offset(rx + eyeR, eyeY + eyeR), strokeW, StrokeCap.Round)
                    drawLine(featureColor, Offset(rx - eyeR, eyeY + eyeR), Offset(rx + eyeR, eyeY - eyeR), strokeW, StrokeCap.Round)
                }
                else -> {
                    // Normal / Listening
                    val currentR = if (emotion == PetEmotion.LISTENING) eyeR * 1.15f else eyeR
                    drawCircle(featureColor, currentR, Offset(lx, eyeY))
                    drawCircle(featureColor, currentR, Offset(rx, eyeY))
                    // Pupils moving
                    drawCircle(pupilColor, currentR * 0.5f, Offset(lx + animLookX, eyeY + animLookY))
                    drawCircle(pupilColor, currentR * 0.5f, Offset(rx + animLookX, eyeY + animLookY))
                    // Highlight
                    drawCircle(Color.White, currentR * 0.2f, Offset(lx + animLookX + 6.dp.toPx(), eyeY + animLookY - 6.dp.toPx()))
                    drawCircle(Color.White, currentR * 0.2f, Offset(rx + animLookX + 6.dp.toPx(), eyeY + animLookY - 6.dp.toPx()))
                }
            }
        }

        // Mouth
        val mouthY = h * 0.75f
        val mouthW = if (emotion == PetEmotion.SURPRISED) 24.dp.toPx() else 36.dp.toPx()
        val mouthX = w / 2f
        
        if (isSpeaking) {
            val speakH = speakMouthAnim.dp.toPx()
            drawOval(featureColor, Offset(mouthX - mouthW/2, mouthY - speakH/2), Size(mouthW, speakH))
        } else {
            when (emotion) {
                PetEmotion.HAPPY, PetEmotion.EXCITED, PetEmotion.LOVE -> {
                    val path = Path().apply {
                        moveTo(mouthX - mouthW/2, mouthY)
                        quadraticTo(mouthX, mouthY + 16.dp.toPx(), mouthX + mouthW/2, mouthY)
                    }
                    drawPath(path, featureColor, style = Stroke(strokeW, cap = StrokeCap.Round))
                }
                PetEmotion.SAD -> {
                    val path = Path().apply {
                        moveTo(mouthX - mouthW/2, mouthY + 8.dp.toPx())
                        quadraticTo(mouthX, mouthY - 8.dp.toPx(), mouthX + mouthW/2, mouthY + 8.dp.toPx())
                    }
                    drawPath(path, featureColor, style = Stroke(strokeW, cap = StrokeCap.Round))
                }
                PetEmotion.SURPRISED -> {
                    drawOval(featureColor, Offset(mouthX - mouthW/2, mouthY), Size(mouthW, 24.dp.toPx()), style = Stroke(strokeW))
                }
                PetEmotion.ERROR -> {
                    val path = Path().apply {
                        moveTo(mouthX - mouthW/2, mouthY)
                        quadraticTo(mouthX - mouthW/4, mouthY - 12.dp.toPx(), mouthX, mouthY)
                        quadraticTo(mouthX + mouthW/4, mouthY + 12.dp.toPx(), mouthX + mouthW/2, mouthY)
                    }
                    drawPath(path, featureColor, style = Stroke(strokeW, cap = StrokeCap.Round))
                }
                PetEmotion.SLEEPY -> {
                    drawOval(featureColor, Offset(mouthX - mouthW/4, mouthY), Size(mouthW/2, 8.dp.toPx()))
                }
                else -> {
                    // Small smile
                    val path = Path().apply {
                        moveTo(mouthX - mouthW/3, mouthY)
                        quadraticTo(mouthX, mouthY + 8.dp.toPx(), mouthX + mouthW/3, mouthY)
                    }
                    drawPath(path, featureColor, style = Stroke(strokeW, cap = StrokeCap.Round))
                }
            }
        }
    }
}
