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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlin.random.Random
import java.util.Calendar

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

// Particle system data class
data class ZzzParticle(
    val id: Int,
    var x: Float,
    var y: Float,
    var alpha: Float,
    val size: Float
)

@Composable
fun VirtualPetScreen(
    modifier: Modifier = Modifier, 
    viewModel: VirtualPetViewModel
) {
    val emotion by viewModel.emotion.collectAsState()
    val bg = Color.Black

    var currentTime by remember { mutableStateOf(java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())) }
    var currentDate by remember { mutableStateOf(java.text.SimpleDateFormat("EEEE, dd/MM/yyyy", java.util.Locale("vi", "VN")).format(java.util.Date())) }
    
    var isNight by remember { mutableStateOf(false) }
    var isNoon by remember { mutableStateOf(false) }

    // Particle state
    val particles = remember { mutableStateListOf<ZzzParticle>() }

    LaunchedEffect(Unit) {
        var pId = 0
        while (true) {
            val date = java.util.Date()
            currentTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(date)
            currentDate = java.text.SimpleDateFormat("EEEE, dd/MM/yyyy", java.util.Locale("vi", "VN")).format(date)
            
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            isNight = hour >= 21 || hour < 6
            isNoon = hour in 11..14
            
            // Zzz Particles emit when sleepy
            if (emotion == PetEmotion.SLEEPY) {
                if (Random.nextFloat() > 0.6f) {
                    particles.add(
                        ZzzParticle(
                            id = pId++,
                            x = Random.nextFloat() * 200 - 100, // random x offset
                            y = 0f,
                            alpha = 1f,
                            size = Random.nextFloat() * 10 + 20f
                        )
                    )
                }
            }
            
            // Update particles
            val iterator = particles.iterator()
            while (iterator.hasNext()) {
                val p = iterator.next()
                p.y -= 5f
                p.x += (Math.sin(p.y / 20.0).toFloat() * 3f) // Wiggle
                p.alpha -= 0.02f
                if (p.alpha <= 0f) {
                    iterator.remove()
                }
            }

            kotlinx.coroutines.delay(50)
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Pet ở giữa
            Box(
                modifier = Modifier.weight(1f).padding(top = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                // Draw particles behind
                Canvas(modifier = Modifier.size(400.dp)) {
                    val cx = size.width / 2
                    val cy = size.height / 2 - 100.dp.toPx()
                    for (p in particles) {
                        drawContext.canvas.nativeCanvas.drawText(
                            "Zzz",
                            cx + p.x,
                            cy + p.y,
                            android.graphics.Paint().apply {
                                color = android.graphics.Color.argb((p.alpha * 255).toInt(), 200, 200, 255)
                                textSize = p.size.dp.toPx()
                            }
                        )
                    }
                }

                AdvancedRobot(
                    emotion = emotion,
                    isNight = isNight,
                    isNoon = isNoon,
                    onTap = { viewModel.poke() },
                    onDoubleTap = { viewModel.doubleTap() },
                    onPet = { viewModel.pet() }
                )
            }
            
            // Đồng hồ nằm dưới
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 80.dp)
            ) {
                Text(
                    text = currentTime,
                    color = Color.White,
                    fontSize = 90.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = currentDate,
                    color = Color.LightGray,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun AdvancedRobot(
    emotion: PetEmotion,
    isNight: Boolean,
    isNoon: Boolean,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
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
        targetValue = if (emotion == PetEmotion.LOVE) 1.5f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (emotion == PetEmotion.LOVE) 600 else 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Ear flaping when excited or happy
    val earFlap by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (emotion == PetEmotion.HAPPY || emotion == PetEmotion.EXCITED || emotion == PetEmotion.LOVE) 20f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(120, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Walking horizontally
    var targetWalk by remember { mutableStateOf(0f) }
    LaunchedEffect(emotion) {
        while (true) {
            delay((2000..6000).random().toLong())
            if (emotion == PetEmotion.IDLE || emotion == PetEmotion.HAPPY || emotion == PetEmotion.THINKING) {
                targetWalk = (-100..100).random().toFloat()
            } else {
                targetWalk = 0f
            }
        }
    }
    val animWalkOffset by animateFloatAsState(targetValue = targetWalk, animationSpec = tween(1500, easing = FastOutSlowInEasing))

    // Dizzy / Sneezing shaking
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
                shakeY = 30f // kéo đầu lùi về sau
                delay(300)
                shakeY = -40f // hắt xì văng lên
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

    // Blinking logic
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
    
    // Eye tracking logic
    var touchPos by remember { mutableStateOf<Offset?>(null) }
    val animLookX by animateFloatAsState(targetValue = touchPos?.x ?: 0f, animationSpec = tween(100))
    val animLookY by animateFloatAsState(targetValue = touchPos?.y ?: 0f, animationSpec = tween(100))

    val bodyColor = Color(0xFFFAF5ED)
    val earColor = Color(0xFFFFB2B2)
    
    // Target ear rotation
    val targetEarRot = when (emotion) {
        PetEmotion.SAD -> -15f
        PetEmotion.LISTENING -> 20f
        PetEmotion.SURPRISED -> 35f
        PetEmotion.EXCITED, PetEmotion.LOVE -> 25f
        PetEmotion.SLEEPY, PetEmotion.DIZZY -> -20f
        PetEmotion.SNEEZING -> -30f // Cụp tai mạnh khi hắt xì
        else -> 0f
    }
    val earRot by animateFloatAsState(targetValue = targetEarRot, animationSpec = tween(300))
    val finalEarRot = earRot + earFlap

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(340.dp)
            .offset(x = animWalkOffset.dp + animShakeX.dp, y = floatAnim.dp + animShakeY.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { onDoubleTap() }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        // Calculate relative to center of screen/robot
                        touchPos = Offset(offset.x / 10 - 15f, offset.y / 10 - 15f)
                    },
                    onDrag = { change, dragAmount ->
                        val pos = change.position
                        // Map touch pos to eyeball constraints roughly
                        touchPos = Offset(pos.x / 10 - 15f, pos.y / 10 - 15f)
                        
                        if (kotlin.math.abs(dragAmount.x) > 10 || kotlin.math.abs(dragAmount.y) > 10) {
                            onPet() // Vuốt mạnh thì kích thích
                        }
                    },
                    onDragEnd = {
                        touchPos = null
                    }
                )
            }
    ) {
        // Draw Ears
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val earW = 44.dp.toPx()
            val earH = 110.dp.toPx()
            val earOffset = 24.dp.toPx()
            
            withTransform({
                translate(left = earOffset, top = h * 0.35f)
                rotate(degrees = -finalEarRot, pivot = Offset(earW, earH/2))
            }) {
                drawRoundRect(color = earColor, size = Size(earW, earH), cornerRadius = CornerRadius(earW/2, earW/2))
            }
            withTransform({
                translate(left = w - earOffset - earW, top = h * 0.35f)
                rotate(degrees = finalEarRot, pivot = Offset(0f, earH/2))
            }) {
                drawRoundRect(color = earColor, size = Size(earW, earH), cornerRadius = CornerRadius(earW/2, earW/2))
            }
        }

        // Draw Body 
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
                        isBlinking = isBlinking,
                        lookOffset = Offset(animLookX, animLookY),
                        isNight = isNight,
                        isNoon = isNoon
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Heart",
                    tint = Color(0xFFFF6B9D),
                    modifier = Modifier.size(44.dp).scale(heartPulse)
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun RobotFaceCanvas(
    emotion: PetEmotion,
    isBlinking: Boolean,
    lookOffset: Offset,
    isNight: Boolean,
    isNoon: Boolean
) {
    val featureColor = Color.White
    val cheekColor = Color(0xFFFF8B94)
    val pupilColor = Color(0xFF222222)
    
    // Eating jaw animation
    val infiniteTransition = rememberInfiniteTransition()
    val eatingJaw by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (emotion == PetEmotion.EATING) 15f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(200, easing = FastOutSlowInEasing),
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
        
        if (emotion != PetEmotion.SLEEPY && emotion != PetEmotion.SNEEZING) {
            val cheekAlpha = if (emotion == PetEmotion.HAPPY || emotion == PetEmotion.LOVE || emotion == PetEmotion.EXCITED) 1f else 0.4f
            drawOval(color = cheekColor.copy(alpha = cheekAlpha), topLeft = Offset(lx - cheekRx, cheekY - cheekRy), size = Size(cheekRx * 2, cheekRy * 2))
            drawOval(color = cheekColor.copy(alpha = cheekAlpha), topLeft = Offset(rx - cheekRx, cheekY - cheekRy), size = Size(cheekRx * 2, cheekRy * 2))
        }

        // Eyes
        val eyeY = h * 0.45f
        val eyeR = 26.dp.toPx()
        val strokeW = 10.dp.toPx()

        if (isBlinking && emotion != PetEmotion.SLEEPY && emotion != PetEmotion.DIZZY && emotion != PetEmotion.SNEEZING) {
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
                    // Dizzy X eyes
                    drawLine(featureColor, Offset(lx - eyeR, eyeY - eyeR), Offset(lx + eyeR, eyeY + eyeR), strokeW, StrokeCap.Round)
                    drawLine(featureColor, Offset(lx - eyeR, eyeY + eyeR), Offset(lx + eyeR, eyeY - eyeR), strokeW, StrokeCap.Round)
                    drawLine(featureColor, Offset(rx - eyeR, eyeY - eyeR), Offset(rx + eyeR, eyeY + eyeR), strokeW, StrokeCap.Round)
                    drawLine(featureColor, Offset(rx - eyeR, eyeY + eyeR), Offset(rx + eyeR, eyeY - eyeR), strokeW, StrokeCap.Round)
                }
                PetEmotion.SNEEZING -> {
                    // Nhắm tịt mũi nhỏ> <
                    drawLine(featureColor, Offset(lx - eyeR, eyeY - eyeR/2), Offset(lx + eyeR, eyeY + eyeR/2), strokeW, StrokeCap.Round)
                    drawLine(featureColor, Offset(lx - eyeR, eyeY + eyeR/2), Offset(lx + eyeR, eyeY - eyeR/2), strokeW, StrokeCap.Round)
                    
                    drawLine(featureColor, Offset(rx - eyeR, eyeY - eyeR/2), Offset(rx + eyeR, eyeY + eyeR/2), strokeW, StrokeCap.Round)
                    drawLine(featureColor, Offset(rx - eyeR, eyeY + eyeR/2), Offset(rx + eyeR, eyeY - eyeR/2), strokeW, StrokeCap.Round)
                }
                else -> {
                    // Normal / Listening / EATING
                    val currentR = if (emotion == PetEmotion.LISTENING) eyeR * 1.15f else eyeR
                    drawCircle(featureColor, currentR, Offset(lx, eyeY))
                    drawCircle(featureColor, currentR, Offset(rx, eyeY))
                    
                    // Clamp look offset dynamically up to inner bounds
                    val maxLook = currentR * 0.4f
                    val lxLook = lookOffset.x.coerceIn(-maxLook, maxLook)
                    val lyLook = lookOffset.y.coerceIn(-maxLook, maxLook)

                    drawCircle(pupilColor, currentR * 0.5f, Offset(lx + lxLook, eyeY + lyLook))
                    drawCircle(pupilColor, currentR * 0.5f, Offset(rx + lxLook, eyeY + lyLook))
                    drawCircle(Color.White, currentR * 0.2f, Offset(lx + lxLook + 6.dp.toPx(), eyeY + lyLook - 6.dp.toPx()))
                    drawCircle(Color.White, currentR * 0.2f, Offset(rx + lxLook + 6.dp.toPx(), eyeY + lyLook - 6.dp.toPx()))
                }
            }
        }

        // Mouth
        val mouthY = h * 0.75f
        val mouthW = if (emotion == PetEmotion.SURPRISED) 24.dp.toPx() else 36.dp.toPx()
        val mouthX = w / 2f
        
        if (emotion == PetEmotion.SNEEZING) {
            val path = Path().apply {
                moveTo(mouthX - 10.dp.toPx(), mouthY + 10.dp.toPx())
                lineTo(mouthX, mouthY)
                lineTo(mouthX + 10.dp.toPx(), mouthY + 10.dp.toPx())
            }
            drawPath(path, featureColor, style = Stroke(strokeW/2, cap = StrokeCap.Round))
        } else if (emotion == PetEmotion.EATING) {
            // Nhai tóp tép
            drawOval(featureColor, Offset(mouthX - mouthW/2, mouthY - eatingJaw/2), Size(mouthW + eatingJaw, eatingJaw + 10.dp.toPx()))
            
            // Vẽ 1 viên năng lượng nhỏ (Battery) cạnh miệng
            drawRoundRect(Color(0xFF6BFF9D), Offset(mouthX + 40.dp.toPx(), mouthY - 10.dp.toPx()), Size(30.dp.toPx(), 20.dp.toPx()), CornerRadius(4.dp.toPx(), 4.dp.toPx()))
            drawRect(Color.White, Offset(mouthX + 70.dp.toPx(), mouthY - 4.dp.toPx()), Size(6.dp.toPx(), 8.dp.toPx()))
        } else {
            when (emotion) {
                PetEmotion.HAPPY, PetEmotion.EXCITED, PetEmotion.LOVE -> {
                    val path = Path().apply {
                        moveTo(mouthX - mouthW/2, mouthY)
                        quadraticTo(mouthX, mouthY + 16.dp.toPx(), mouthX + mouthW/2, mouthY)
                    }
                    drawPath(path, featureColor, style = Stroke(strokeW, cap = StrokeCap.Round))
                }
                PetEmotion.SAD, PetEmotion.ERROR, PetEmotion.DIZZY -> {
                    val path = Path().apply {
                        moveTo(mouthX - mouthW/2, mouthY + 10.dp.toPx())
                        quadraticTo(mouthX, mouthY - 6.dp.toPx(), mouthX + mouthW/2, mouthY + 10.dp.toPx())
                    }
                    drawPath(path, featureColor, style = Stroke(strokeW, cap = StrokeCap.Round))
                }
                PetEmotion.SURPRISED -> {
                    drawOval(featureColor, Offset(mouthX - mouthW/2, mouthY), Size(mouthW, mouthW * 1.5f))
                }
                PetEmotion.THINKING -> {
                    drawLine(featureColor, Offset(mouthX - mouthW/2, mouthY), Offset(mouthX + mouthW/4, mouthY), strokeW, StrokeCap.Round)
                }
                PetEmotion.SLEEPY -> {
                    drawOval(featureColor, Offset(mouthX - mouthW/4, mouthY), Size(mouthW/2, mouthW/2))
                }
                else -> {
                    drawLine(featureColor, Offset(mouthX - mouthW/3, mouthY), Offset(mouthX + mouthW/3, mouthY), strokeW, StrokeCap.Round)
                }
            }
        }
        
        // ------------- Phụ Kiện (Accessories) -------------
        if (isNoon) {
            // Vẽ Kính Râm (Thug life / Sunglasses) lúc trưa nắng
            val glassY = eyeY - 20.dp.toPx()
            val glassH = 50.dp.toPx()
            
            // Mắt kính trái và phải
            val glassPath = Path().apply {
                moveTo(lx - 40.dp.toPx(), glassY)
                lineTo(lx + 30.dp.toPx(), glassY)
                lineTo(lx + 20.dp.toPx(), glassY + glassH)
                lineTo(lx - 30.dp.toPx(), glassY + glassH * 0.8f)
                close()
                
                moveTo(rx - 30.dp.toPx(), glassY)
                lineTo(rx + 40.dp.toPx(), glassY)
                lineTo(rx + 30.dp.toPx(), glassY + glassH * 0.8f)
                lineTo(rx - 20.dp.toPx(), glassY + glassH)
                close()
            }
            drawPath(glassPath, Color(0xFF1E1E1E))
            
            // Gọng kính nối ngang
            drawLine(Color(0xFF1E1E1E), Offset(lx + 25.dp.toPx(), glassY + 10.dp.toPx()), Offset(rx - 25.dp.toPx(), glassY + 10.dp.toPx()), 8.dp.toPx(), StrokeCap.Round)
        }
        
        if (isNight && emotion == PetEmotion.SLEEPY) {
            // Vẽ Mũ Lưỡi Trai Đi Ngủ (Night Cap) khi ngủ
            val hatPath = Path().apply {
                moveTo(w * 0.2f, 10.dp.toPx()) // Viền trái của mũ trên đỉnh đầu
                quadraticTo(w * 0.5f, -30.dp.toPx(), w * 0.8f, 10.dp.toPx()) // Bo tròn qua phải
                quadraticTo(w * 0.95f, 60.dp.toPx(), w * 0.85f, 100.dp.toPx()) // Chuôi mũ rủ xuống phải
                lineTo(w * 0.75f, 95.dp.toPx())
                quadraticTo(w * 0.85f, 40.dp.toPx(), w * 0.7f, 20.dp.toPx()) // Cong bóp vào
                close()
            }
            drawPath(hatPath, Color(0xFF92B4FF))
            // Chút bông mũ
            drawCircle(Color.White, 16.dp.toPx(), Offset(w * 0.85f, 100.dp.toPx()))
            // Vành mũ
            drawRoundRect(Color.White, Offset(w * 0.15f, 10.dp.toPx()), Size(w * 0.7f, 24.dp.toPx()), CornerRadius(12.dp.toPx(), 12.dp.toPx()))
        }
    }
}
