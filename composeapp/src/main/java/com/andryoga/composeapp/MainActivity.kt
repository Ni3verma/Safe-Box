package com.andryoga.composeapp

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.andryoga.composeapp.ui.theme.SafeBoxTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SafeBoxTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LoginScreen()
                }
            }
        }
    }
}
@Composable
fun LoginScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
//            .background(color = colorScheme.primary)
    ) {
        // ===== Beautiful Curvy Background =====
        AnimatedRainbowWavesBackground()
//        WavesBackground()

        // ===== Welcome Back! text =====
        Text(
            text = "Welcome Back!",
            color = colorScheme.onPrimary,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
        )

        // ===== Card in Middle =====
        Card(
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(12.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp)
        ) {
            LoginCardContent()
        }
    }
}

@Composable
fun LoginCardContent() {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .padding(20.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Password Input with Eye Toggle
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image =
                    if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(image, contentDescription = "Toggle Password")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Show Hint Button
        TextButton(onClick = { showHint = !showHint }) {
            Text("Show Hint")
        }

        // Hint Text
        if (showHint) {
            Text(
                text = "This is hint",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Login Button
        Button(
            onClick = { /* TODO: Handle Login */ },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Login")
        }
    }
}

@Composable
fun AnimatedRainbowWavesBackground() {
    // Infinite animation for wave motion
    val infiniteTransition = rememberInfiniteTransition(label = "waveAnim")

    val waveShift by infiniteTransition.animateFloat(
        initialValue = -150f,
        targetValue = 150f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveShift"
    )

    // Infinite animation for gradient offset
    val gradientShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f, // arbitrary shift distance
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientShift"
    )

    val primaryColor = colorScheme.primary
    val secondaryColor = colorScheme.secondary


    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // 🎨 Animated rainbow gradient
        val gradientBrush = Brush.linearGradient(
//            colors = listOf(
////                Color(0xFF6A11CB), // violet
//                Color(0xFF2575FC), // blue
//                Color(0xFF00C9A7), // teal
//                Color(0xFFFFA500), // orange
//                Color(0xFFE94057)  // pink-red
//            ),
            colors = listOf(
               primaryColor,
                Color(0xFFE94057),


            ),
            start = Offset(0f + gradientShift, 0f),
            end = Offset(width + gradientShift, height)
        )

        val overlayBrush = Brush.radialGradient(
            colors = listOf(Color(0x44FFFFFF), Color.Transparent),
            center = Offset(width / 2, height * 0.2f),
            radius = width * 0.8f
        )

        // Wave Path 1
        val path1 = Path().apply {
            moveTo(0f, height * 0.3f)
            quadraticBezierTo(
                width * 0.5f,
                height * 0.15f + waveShift,
                width,
                height * 0.3f
            )
            lineTo(width, 0f)
            lineTo(0f, 0f)
            close()
        }
        drawPath(path1, brush = gradientBrush)

        // Wave Path 2 (overlay)
        val path2 = Path().apply {
            moveTo(0f, height * 0.35f)
            quadraticBezierTo(
                width * 0.5f,
                height * 0.2f - waveShift,
                width,
                height * 0.35f
            )
            lineTo(width, 0f)
            lineTo(0f, 0f)
            close()
        }
        drawPath(path2, brush = overlayBrush)
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun GreetingPreview() {
    SafeBoxTheme {
        LoginScreen()
    }
}