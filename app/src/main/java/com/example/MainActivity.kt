package com.example

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.sqrt
import kotlin.random.Random

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
            
            if (acceleration > 15) {
                viewModel.onShake()
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    VirtualPetScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = viewModel
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

enum class PetParticleType { ZZZ, HEART, TEAR }
data class PetParticle(
    val id: Int,
    var x: Float,
    var y: Float,
    var alpha: Float,
    val size: Float,
    val type: PetParticleType = PetParticleType.ZZZ
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    viewModel: VirtualPetViewModel,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    val sleepHour by viewModel.sleepHour.collectAsState()
    val wakeHour by viewModel.wakeHour.collectAsState()
    val customReminder by viewModel.customReminder.collectAsState()
    val petName by viewModel.petName.collectAsState()
    val isMuteIdleSounds by viewModel.isMuteIdleSounds.collectAsState()
    val customTasksList by viewModel.customTasks.collectAsState()

    var inputSleep by remember { mutableStateOf(sleepHour.toString()) }
    var inputWake by remember { mutableStateOf(wakeHour.toString()) }
    var inputReminder by remember { mutableStateOf(customReminder) }
    var inputName by remember { mutableStateOf(petName) }
    var inputMute by remember { mutableStateOf(isMuteIdleSounds) }
    
    val editTasks = remember { mutableStateListOf<CustomTask>().apply { addAll(customTasksList) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        ),
        modifier = Modifier.fillMaxWidth(0.9f).padding(WindowInsets.ime.asPaddingValues()),
        title = { Text("Quản gia Petbot") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = inputName,
                    onValueChange = { inputName = it },
                    label = { Text("Tên Pet") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = inputSleep,
                        onValueChange = { inputSleep = it },
                        label = { Text("Giờ ngủ") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = inputWake,
                        onValueChange = { inputWake = it },
                        label = { Text("Giờ thức") },
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = inputReminder,
                    onValueChange = { inputReminder = it },
                    label = { Text("Lời nhắc báo thức") },
                    singleLine = true
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = inputMute, onCheckedChange = { inputMute = it })
                    Text("Tắt vô tri ban ngày", fontSize = 14.sp)
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text("Nhắc việc tùy chỉnh (${editTasks.size}/5)", style = MaterialTheme.typography.titleMedium)
                editTasks.forEachIndexed { index, task ->
                    Surface(
                        color = Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth() // Nested rows
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    modifier = Modifier.weight(0.4f),
                                    value = task.time,
                                    onValueChange = { editTasks[index] = task.copy(time = it) },
                                    label = { Text("Giờ") },
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    modifier = Modifier.weight(0.6f),
                                    value = task.repeatCount.toString(),
                                    onValueChange = { 
                                        val newCount = it.toIntOrNull()
                                        if (newCount != null) {
                                            editTasks[index] = task.copy(repeatCount = newCount) 
                                        }
                                    },
                                    label = { Text("Lặp lại (lần)") },
                                    singleLine = true
                                )
                                IconButton(onClick = { editTasks.removeAt(index) }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Xóa", tint = Color.Red)
                                }
                            }
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = task.content,
                                onValueChange = { editTasks[index] = task.copy(content = it) },
                                label = { Text("Nội dung lời nhắc") },
                                singleLine = true
                            )
                        }
                    }
                }
                if (editTasks.size < 5) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { editTasks.add(CustomTask()) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) {
                        Text("+ Thêm hẹn giờ mới")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val sh = inputSleep.toIntOrNull() ?: 22
                val wh = inputWake.toIntOrNull() ?: 6
                viewModel.saveConfig(
                    sleepH = sh.coerceIn(0, 23),
                    wakeH = wh.coerceIn(0, 23),
                    reminder = inputReminder,
                    name = inputName,
                    mute = inputMute
                )
                viewModel.saveTasks(editTasks.toList())
                onDismiss()
            }) {
                Text("Lưu cài đặt")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Đóng") }
        }
    )
}

@Composable
fun VirtualPetScreen(
    modifier: Modifier = Modifier, 
    viewModel: VirtualPetViewModel
) {
    val speechRecognizerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.get(0)
            if (spokenText != null) {
                viewModel.processVoiceCommand(spokenText)
            }
        }
    }

    val emotion by viewModel.emotion.collectAsState()
    val isDeepNightMode by viewModel.isDeepNightMode.collectAsState()

    var currentTime by remember { mutableStateOf(java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())) }
    var currentDate by remember { mutableStateOf(java.text.SimpleDateFormat("EEEE, dd/MM/yyyy", java.util.Locale("vi", "VN")).format(java.util.Date())) }
    
    var showSettings by remember { mutableStateOf(false) }

    val particles = remember { mutableStateListOf<PetParticle>() }

    LaunchedEffect(Unit) {
        var pId = 0
        while (true) {
            val date = java.util.Date()
            currentTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(date)
            currentDate = java.text.SimpleDateFormat("EEEE, dd/MM/yyyy", java.util.Locale("vi", "VN")).format(date)
            
            if (emotion == PetEmotion.SLEEPY && !isDeepNightMode) {
                if (Random.nextFloat() > 0.6f) {
                    particles.add(PetParticle(id = pId++, x = Random.nextFloat() * 200 - 100, y = 0f, alpha = 1f, size = Random.nextFloat() * 10 + 20f, type = PetParticleType.ZZZ))
                }
            } else if (emotion == PetEmotion.LOVE) {
                if (Random.nextFloat() > 0.4f) {
                    particles.add(PetParticle(id = pId++, x = Random.nextFloat() * 300 - 150, y = 100f, alpha = 1f, size = Random.nextFloat() * 20 + 20f, type = PetParticleType.HEART))
                }
            } else if (emotion == PetEmotion.SAD || emotion == PetEmotion.SNEEZING) {
                if (Random.nextFloat() > 0.3f) {
                    particles.add(PetParticle(id = pId++, x = Random.nextFloat() * 200 - 100, y = -50f, alpha = 1f, size = Random.nextFloat() * 10 + 15f, type = PetParticleType.TEAR))
                }
            }
            
            val iterator = particles.iterator()
            while (iterator.hasNext()) {
                val p = iterator.next()
                when (p.type) {
                    PetParticleType.ZZZ -> { p.y -= 5f; p.x += (Math.sin(p.y / 20.0).toFloat() * 3f); p.alpha -= 0.02f }
                    PetParticleType.HEART -> { p.y -= 8f; p.x += (Math.sin(p.y / 15.0).toFloat() * 4f); p.alpha -= 0.03f }
                    PetParticleType.TEAR -> { p.y += 10f; p.alpha -= 0.04f }
                }
                if (p.alpha <= 0f) {
                    iterator.remove()
                }
            }
            kotlinx.coroutines.delay(50)
        }
    }

    if (showSettings) {
        SettingsDialog(viewModel = viewModel, onDismiss = { showSettings = false })
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (isDeepNightMode) {
            // Chế độ Màn hình Đồng hồ Siêu Tối Nằm Ngang
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentTime,
                    color = Color(0xFF1E1E1E), // Xám siêu tối, lờ mờ
                    fontSize = 260.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
            }
            
            // Nút Menu chìm, bấm vào ra Settings
            IconButton(
                onClick = { showSettings = true },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color(0xFF1E1E1E))
            }

        } else {
            // Chế độ Hành Động Vô Tri Bình Thường Nằm Ngang (Row 40/60)
            Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bên trái 40% (Giờ và ngày)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(0.4f)
                ) {
                    Text(
                        text = currentTime,
                        color = Color.White,
                        fontSize = 110.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentDate,
                        color = Color.LightGray,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Bên phải 60% (Robot và Hạt bụi)
                Box(
                    modifier = Modifier.weight(0.6f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(400.dp)) {
                        val cx = size.width / 2
                        val cy = size.height / 2 - 100.dp.toPx()
                        for (p in particles) {
                            try {
                                if (p.type == PetParticleType.ZZZ) {
                                    drawContext.canvas.nativeCanvas.drawText("Zzz", cx + p.x, cy + p.y, android.graphics.Paint().apply { color = android.graphics.Color.argb((p.alpha * 255).toInt(), 200, 200, 255); textSize = p.size.dp.toPx() })
                                } else if (p.type == PetParticleType.HEART) {
                                    drawContext.canvas.nativeCanvas.drawText("❤", cx + p.x, cy + p.y, android.graphics.Paint().apply { color = android.graphics.Color.argb((p.alpha * 255).toInt(), 255, 120, 150); textSize = p.size.dp.toPx() })
                                } else if (p.type == PetParticleType.TEAR) {
                                    drawContext.canvas.nativeCanvas.drawText("💧", cx + p.x, cy + p.y, android.graphics.Paint().apply { color = android.graphics.Color.argb((p.alpha * 255).toInt(), 120, 200, 255); textSize = p.size.dp.toPx() })
                                }
                            } catch (e: Exception) {}
                        }
                    }

                    AdvancedRobot(
                        emotion = emotion,
                        onTap = { viewModel.poke() },
                        onDoubleTap = { viewModel.doubleTap() },
                        onLongPress = {
                            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Đọc khẩu lệnh cho Pet...")
                            }
                            try {
                                speechRecognizerLauncher.launch(intent)
                            } catch (e: Exception) {}
                        },
                        onPet = { viewModel.pet() }
                    )
                }
            }

            IconButton(
                onClick = { showSettings = true },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.Gray)
            }
        }
    }
}

@Composable
fun AdvancedRobot(
    emotion: PetEmotion,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
    onPet: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    val floatAnim by infiniteTransition.animateFloat(
        initialValue = -8f, targetValue = 8f,
        animationSpec = infiniteRepeatable(animation = tween(2000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse)
    )
    
    val heartPulse by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = if (emotion == PetEmotion.LOVE) 1.5f else 1.05f,
        animationSpec = infiniteRepeatable(animation = tween(if (emotion == PetEmotion.LOVE) 600 else 1000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse)
    )

    val earFlap by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = if (emotion == PetEmotion.HAPPY || emotion == PetEmotion.EXCITED || emotion == PetEmotion.LOVE || emotion == PetEmotion.DANCING) 20f else 0f,
        animationSpec = infiniteRepeatable(animation = tween(if (emotion == PetEmotion.DANCING) 60 else 120, easing = LinearEasing), repeatMode = RepeatMode.Reverse)
    )

    val danceScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = if (emotion == PetEmotion.DANCING) 1.15f else 1f,
        animationSpec = infiniteRepeatable(animation = tween(if (emotion == PetEmotion.DANCING) 250 else 1000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse)
    )

    var targetWalk by remember { mutableStateOf(0f) }
    LaunchedEffect(emotion) {
        while (true) {
            delay(if (emotion == PetEmotion.DANCING) 400 else (2000..6000).random().toLong())
            targetWalk = if (emotion == PetEmotion.IDLE || emotion == PetEmotion.HAPPY || emotion == PetEmotion.THINKING) (-100..100).random().toFloat() else if (emotion == PetEmotion.DANCING) (-150..150).random().toFloat() else 0f
        }
    }
    val animWalkOffset by animateFloatAsState(targetValue = targetWalk, animationSpec = tween(if (emotion == PetEmotion.DANCING) 400 else 1500, easing = FastOutSlowInEasing))

    var shakeX by remember { mutableStateOf(0f) }
    var shakeY by remember { mutableStateOf(0f) }
    LaunchedEffect(emotion) {
        while (true) {
            if (emotion == PetEmotion.DIZZY) {
                shakeX = (-20..20).random().toFloat()
                shakeY = (-20..20).random().toFloat()
                delay(50)
            } else if (emotion == PetEmotion.SNEEZING) {
                shakeX = 0f
                shakeY = 30f 
                delay(300)
                shakeY = -40f 
                delay(100)
                shakeX = (-10..10).random().toFloat()
                delay(50)
                shakeX = 0f
                shakeY = 0f
                delay(2000)
            } else {
                shakeX = 0f
                shakeY = 0f
                delay(1000)
            }
        }
    }
    val animShakeX by animateFloatAsState(targetValue = shakeX, animationSpec = tween(if (emotion == PetEmotion.DIZZY) 50 else 100))
    val animShakeY by animateFloatAsState(targetValue = shakeY, animationSpec = tween(if (emotion == PetEmotion.DIZZY) 50 else 100))

    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(emotion) {
        while (true) {
            delay((2000..6000).random().toLong())
            if (emotion != PetEmotion.SLEEPY && emotion != PetEmotion.DIZZY && emotion != PetEmotion.SNEEZING) {
                isBlinking = true
                delay(150)
                isBlinking = false
                if (Math.random() > 0.7) {
                    delay(100)
                    isBlinking = true
                    delay(150)
                    isBlinking = false
                }
            }
        }
    }
    
    var touchPos by remember { mutableStateOf<Offset?>(null) }
    val animLookX by animateFloatAsState(targetValue = touchPos?.x ?: 0f, animationSpec = tween(100))
    val animLookY by animateFloatAsState(targetValue = touchPos?.y ?: 0f, animationSpec = tween(100))

    val bodyColor = Color(0xFFFAF5ED)
    val earColor = Color(0xFFFFB2B2)
    
    val targetEarRot = when (emotion) {
        PetEmotion.SAD -> -15f
        PetEmotion.LISTENING -> 20f
        PetEmotion.SURPRISED -> 35f
        PetEmotion.EXCITED, PetEmotion.LOVE -> 25f
        PetEmotion.SLEEPY, PetEmotion.DIZZY -> -20f
        PetEmotion.SNEEZING -> -30f
        PetEmotion.DANCING -> 40f
        PetEmotion.EAVESDROPPING -> 90f // Chĩa ngang tai trái
        else -> 0f
    }
    val earRot by animateFloatAsState(targetValue = targetEarRot, animationSpec = tween(300))
    
    val earLeftScaleTarget = if (emotion == PetEmotion.EAVESDROPPING) 1.6f else 1f
    val earLeftScale by animateFloatAsState(targetValue = earLeftScaleTarget, animationSpec = tween(400))
    
    val finalEarLeftRot = earRot + earFlap
    val finalEarRightRot = earRot - (if (emotion == PetEmotion.DANCING) earFlap else -earFlap)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(340.dp)
            .offset(x = animWalkOffset.dp + animShakeX.dp, y = floatAnim.dp + animShakeY.dp)
            .scale(danceScale)
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onDoubleTap = { onDoubleTap() }, onLongPress = { onLongPress() }) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> touchPos = Offset(offset.x / 10 - 15f, offset.y / 10 - 15f) },
                    onDrag = { change, dragAmount ->
                        val pos = change.position
                        touchPos = Offset(pos.x / 10 - 15f, pos.y / 10 - 15f)
                        if (kotlin.math.abs(dragAmount.x) > 10 || kotlin.math.abs(dragAmount.y) > 10) onPet()
                    },
                    onDragEnd = { touchPos = null }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val earW = 44.dp.toPx()
            val earH = 110.dp.toPx()
            val earOffset = 24.dp.toPx()
            
            withTransform({ translate(left = earOffset, top = h * 0.35f); rotate(degrees = -finalEarLeftRot, pivot = Offset(earW, earH/2)); scale(scaleX = 1f, scaleY = earLeftScale, pivot = Offset(earW, earH)) }) {
                drawRoundRect(color = earColor, size = Size(earW, earH), cornerRadius = CornerRadius(earW/2, earW/2))
            }
            withTransform({ translate(left = w - earOffset - earW, top = h * 0.35f); rotate(degrees = finalEarRightRot, pivot = Offset(0f, earH/2)) }) {
                drawRoundRect(color = earColor, size = Size(earW, earH), cornerRadius = CornerRadius(earW/2, earW/2))
            }
        }

        Surface(
            modifier = Modifier.size(width = 280.dp, height = 300.dp),
            shape = RoundedCornerShape(80.dp),
            color = bodyColor,
            shadowElevation = 24.dp,
            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFF0E5D8))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier.size(width = 248.dp, height = 200.dp).background(Brush.radialGradient(colors = listOf(Color(0xFF2A2A35), Color(0xFF0D0D12)), radius = 500f), shape = RoundedCornerShape(70.dp)).border(4.dp, Color(0xFF050505), RoundedCornerShape(70.dp))
                ) {
                    RobotFaceCanvas(emotion = emotion, isBlinking = isBlinking, lookOffset = Offset(animLookX, animLookY))
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(imageVector = Icons.Default.Favorite, contentDescription = "Heart", tint = Color(0xFFFF6B9D), modifier = Modifier.size(44.dp).scale(heartPulse))
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun RobotFaceCanvas(
    emotion: PetEmotion,
    isBlinking: Boolean,
    lookOffset: Offset
) {
    val featureColor = Color.White
    val cheekColor = Color(0xFFFF8B94)
    val pupilColor = Color(0xFF222222)
    
    val infiniteTransition = rememberInfiniteTransition()
    val eatingJaw by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = if (emotion == PetEmotion.EATING) 15f else 0f,
        animationSpec = infiniteRepeatable(animation = tween(200, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse)
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        
        val cheekY = h * 0.65f
        val cheekRx = 18.dp.toPx()
        val cheekRy = 10.dp.toPx()
        val lx = w * 0.25f
        val rx = w * 0.75f
        
        if (emotion != PetEmotion.SLEEPY && emotion != PetEmotion.SNEEZING) {
            val cheekAlpha = if (emotion == PetEmotion.HAPPY || emotion == PetEmotion.LOVE || emotion == PetEmotion.EXCITED) 1f else 0.4f
            drawOval(color = cheekColor.copy(alpha = cheekAlpha), topLeft = Offset(lx - cheekRx, cheekY - cheekRy), size = Size(cheekRx * 2, cheekRy * 2))
            drawOval(color = cheekColor.copy(alpha = cheekAlpha), topLeft = Offset(rx - cheekRx, cheekY - cheekRy), size = Size(cheekRx * 2, cheekRy * 2))
        }

        val eyeY = h * 0.45f
        val eyeR = 26.dp.toPx()
        val strokeW = 10.dp.toPx()

        if (isBlinking && emotion != PetEmotion.SLEEPY && emotion != PetEmotion.DIZZY && emotion != PetEmotion.SNEEZING) {
            drawLine(featureColor, Offset(lx - eyeR, eyeY), Offset(lx + eyeR, eyeY), strokeW, StrokeCap.Round)
            drawLine(featureColor, Offset(rx - eyeR, eyeY), Offset(rx + eyeR, eyeY), strokeW, StrokeCap.Round)
        } else {
            when (emotion) {
                PetEmotion.HAPPY -> {
                    drawArc(featureColor, 180f, 180f, false, Offset(lx - eyeR, eyeY - eyeR), Size(eyeR*2, eyeR*2), style = Stroke(strokeW, cap = StrokeCap.Round))
                    drawArc(featureColor, 180f, 180f, false, Offset(rx - eyeR, eyeY - eyeR), Size(eyeR*2, eyeR*2), style = Stroke(strokeW, cap = StrokeCap.Round))
                }
                PetEmotion.EXCITED -> {
                    val drawStar = { center: Offset ->
                        val path = Path().apply {
                            val outR = eyeR * 1.5f
                            val inR = eyeR * 0.6f
                            moveTo(center.x, center.y - outR)
                            for (i in 1..9) {
                                val radius = if (i % 2 == 0) outR else inR
                                val angle = Math.PI / 2.0 - i * Math.PI / 5.0
                                lineTo(center.x + (kotlin.math.cos(angle) * radius).toFloat(), center.y - (kotlin.math.sin(angle) * radius).toFloat())
                            }
                            close()
                        }
                        drawPath(path, featureColor, style = Stroke(strokeW / 2, cap = StrokeCap.Round))
                    }
                    drawStar(Offset(lx, eyeY))
                    drawStar(Offset(rx, eyeY))
                }
                PetEmotion.SAD -> {
                    drawLine(featureColor, Offset(lx - eyeR, eyeY - eyeR/4), Offset(lx + eyeR, eyeY + eyeR/2), strokeW, StrokeCap.Round)
                    drawLine(featureColor, Offset(rx - eyeR, eyeY + eyeR/2), Offset(rx + eyeR, eyeY - eyeR/4), strokeW, StrokeCap.Round)
                    val tearPath = Path().apply { moveTo(lx, eyeY + eyeR); quadraticTo(lx + 8.dp.toPx(), eyeY + eyeR + 15.dp.toPx(), lx, eyeY + eyeR + 30.dp.toPx()); quadraticTo(lx - 8.dp.toPx(), eyeY + eyeR + 15.dp.toPx(), lx, eyeY + eyeR) }
                    drawPath(tearPath, Color(0xFF88CCFF))
                    val tearPath2 = Path().apply { moveTo(rx, eyeY + eyeR); quadraticTo(rx + 8.dp.toPx(), eyeY + eyeR + 15.dp.toPx(), rx, eyeY + eyeR + 30.dp.toPx()); quadraticTo(rx - 8.dp.toPx(), eyeY + eyeR + 15.dp.toPx(), rx, eyeY + eyeR) }
                    drawPath(tearPath2, Color(0xFF88CCFF))
                }
                PetEmotion.SLEEPY -> {
                    drawLine(featureColor, Offset(lx - eyeR, eyeY), Offset(lx + eyeR, eyeY), strokeW, StrokeCap.Round)
                    drawLine(featureColor, Offset(rx - eyeR, eyeY), Offset(rx + eyeR, eyeY), strokeW, StrokeCap.Round)
                }
                PetEmotion.SURPRISED -> {
                    drawCircle(featureColor, eyeR * 1.2f, Offset(lx, eyeY))
                    drawCircle(featureColor, eyeR * 1.2f, Offset(rx, eyeY))
                    drawCircle(pupilColor, eyeR * 0.4f, Offset(lx, eyeY))
                    drawCircle(pupilColor, eyeR * 0.4f, Offset(rx, eyeY))
                }
                PetEmotion.LOVE -> {
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
                PetEmotion.ERROR, PetEmotion.DIZZY -> {
                    drawLine(featureColor, Offset(lx - eyeR, eyeY - eyeR), Offset(lx + eyeR, eyeY + eyeR), strokeW, StrokeCap.Round)
                    drawLine(featureColor, Offset(lx - eyeR, eyeY + eyeR), Offset(lx + eyeR, eyeY - eyeR), strokeW, StrokeCap.Round)
                    drawLine(featureColor, Offset(rx - eyeR, eyeY - eyeR), Offset(rx + eyeR, eyeY + eyeR), strokeW, StrokeCap.Round)
                    drawLine(featureColor, Offset(rx - eyeR, eyeY + eyeR), Offset(rx + eyeR, eyeY - eyeR), strokeW, StrokeCap.Round)
                }
                PetEmotion.SNEEZING -> {
                    drawLine(featureColor, Offset(lx - eyeR, eyeY - eyeR/2), Offset(lx + eyeR, eyeY + eyeR/2), strokeW, StrokeCap.Round)
                    drawLine(featureColor, Offset(lx - eyeR, eyeY + eyeR/2), Offset(lx + eyeR, eyeY - eyeR/2), strokeW, StrokeCap.Round)
                    drawLine(featureColor, Offset(rx - eyeR, eyeY - eyeR/2), Offset(rx + eyeR, eyeY + eyeR/2), strokeW, StrokeCap.Round)
                    drawLine(featureColor, Offset(rx - eyeR, eyeY + eyeR/2), Offset(rx + eyeR, eyeY - eyeR/2), strokeW, StrokeCap.Round)
                }
                else -> {
                    val currentR = if (emotion == PetEmotion.LISTENING) eyeR * 1.15f else eyeR
                    drawCircle(featureColor, currentR, Offset(lx, eyeY))
                    drawCircle(featureColor, currentR, Offset(rx, eyeY))
                    val maxLook = currentR * 0.4f
                    var lxLook = lookOffset.x.coerceIn(-maxLook, maxLook)
                    var lyLook = lookOffset.y.coerceIn(-maxLook, maxLook)
                    
                    if (emotion == PetEmotion.PLAYING_PHONE) {
                        lxLook = 0f
                        lyLook = maxLook * 0.9f
                    } else if (emotion == PetEmotion.EAVESDROPPING) {
                        lxLook = -maxLook * 0.9f
                        lyLook = 0f
                    }

                    drawCircle(pupilColor, currentR * 0.5f, Offset(lx + lxLook, eyeY + lyLook))
                    drawCircle(pupilColor, currentR * 0.5f, Offset(rx + lxLook, eyeY + lyLook))
                    drawCircle(Color.White, currentR * 0.2f, Offset(lx + lxLook + 6.dp.toPx(), eyeY + lyLook - 6.dp.toPx()))
                    drawCircle(Color.White, currentR * 0.2f, Offset(rx + lxLook + 6.dp.toPx(), eyeY + lyLook - 6.dp.toPx()))
                }
            }
        }

        val mouthY = h * 0.75f
        val mouthW = if (emotion == PetEmotion.SURPRISED) 24.dp.toPx() else 36.dp.toPx()
        val mouthX = w / 2f
        
        if (emotion == PetEmotion.SNEEZING) {
            val path = Path().apply { moveTo(mouthX - 10.dp.toPx(), mouthY + 10.dp.toPx()); lineTo(mouthX, mouthY); lineTo(mouthX + 10.dp.toPx(), mouthY + 10.dp.toPx()) }
            drawPath(path, featureColor, style = Stroke(strokeW/2, cap = StrokeCap.Round))
        } else if (emotion == PetEmotion.EATING) {
            drawOval(featureColor, Offset(mouthX - mouthW/2, mouthY - eatingJaw/2), Size(mouthW + eatingJaw, eatingJaw + 10.dp.toPx()))
            drawRoundRect(Color(0xFF6BFF9D), Offset(mouthX + 40.dp.toPx(), mouthY - 10.dp.toPx()), Size(30.dp.toPx(), 20.dp.toPx()), CornerRadius(4.dp.toPx(), 4.dp.toPx()))
            drawRect(Color.White, Offset(mouthX + 70.dp.toPx(), mouthY - 4.dp.toPx()), Size(6.dp.toPx(), 8.dp.toPx()))
        } else {
            when (emotion) {
                PetEmotion.HAPPY, PetEmotion.EXCITED, PetEmotion.LOVE -> {
                    val path = Path().apply { moveTo(mouthX - mouthW/2, mouthY); quadraticTo(mouthX, mouthY + 16.dp.toPx(), mouthX + mouthW/2, mouthY) }
                    drawPath(path, featureColor, style = Stroke(strokeW, cap = StrokeCap.Round))
                }
                PetEmotion.SAD, PetEmotion.ERROR, PetEmotion.DIZZY -> {
                    val path = Path().apply { moveTo(mouthX - mouthW/2, mouthY + 10.dp.toPx()); quadraticTo(mouthX, mouthY - 6.dp.toPx(), mouthX + mouthW/2, mouthY + 10.dp.toPx()) }
                    drawPath(path, featureColor, style = Stroke(strokeW, cap = StrokeCap.Round))
                }
                PetEmotion.SURPRISED -> drawOval(featureColor, Offset(mouthX - mouthW/2, mouthY), Size(mouthW, mouthW * 1.5f))
                PetEmotion.THINKING -> drawLine(featureColor, Offset(mouthX - mouthW/2, mouthY), Offset(mouthX + mouthW/4, mouthY), strokeW, StrokeCap.Round)
                PetEmotion.SLEEPY -> drawOval(featureColor, Offset(mouthX - mouthW/4, mouthY), Size(mouthW/2, mouthW/2))
                else -> drawLine(featureColor, Offset(mouthX - mouthW/3, mouthY), Offset(mouthX + mouthW/3, mouthY), strokeW, StrokeCap.Round)
            }
        }

        if (emotion == PetEmotion.PLAYING_PHONE) {
            val phoneW = 80.dp.toPx()
            val phoneH = 50.dp.toPx()
            val phoneY = h * 0.85f
            // Vẽ màn hình hắt sáng cực quang
            drawOval(Brush.radialGradient(listOf(Color(0x335588FF), Color.Transparent), center = Offset(w/2, h * 0.8f), radius = 150.dp.toPx()), Offset(w/2 - 150.dp.toPx(), h * 0.4f), Size(300.dp.toPx(), 300.dp.toPx()))
            
            // Vẽ thân điện thoại
            drawRoundRect(Color(0xFF222222), Offset(w/2 - phoneW/2, phoneY), Size(phoneW, phoneH), CornerRadius(10.dp.toPx(), 10.dp.toPx()))
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF88BBFF), Color(0xFF2255CC)), startY = phoneY, endY = phoneY + phoneH), Offset(w/2 - phoneW/2 + 4.dp.toPx(), phoneY + 4.dp.toPx()), Size(phoneW - 8.dp.toPx(), phoneH - 4.dp.toPx()), CornerRadius(6.dp.toPx(), 6.dp.toPx()))
        }
    }
}
