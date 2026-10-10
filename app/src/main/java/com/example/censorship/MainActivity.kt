package com.example.censorship

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.censorship.ui.theme.CensorshipTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CensorshipTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFFAF9F6)) {
                    val context = LocalContext.current
                    val settingsManager = remember { SettingsManager(context) }
                    
                    var isUnlocked by remember { mutableStateOf(settingsManager.pinCode == null) }
                    
                    if (isUnlocked) {
                        CensorAppUI(settingsManager)
                    } else {
                        PinLockScreen(settingsManager.pinCode!!) {
                            isUnlocked = true
                        }
                    }
                }
            }
        }
    }
}

val SketchyShape = RoundedCornerShape(24.dp, 8.dp, 24.dp, 8.dp)
val SketchyShapeAlt = RoundedCornerShape(8.dp, 24.dp, 8.dp, 24.dp)

@Composable
fun PinLockScreen(expectedPin: String, onUnlock: () -> Unit) {
    var enteredPin by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Parental Lock", fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Cursive)
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = enteredPin,
            onValueChange = { 
                if (it.length <= 4) enteredPin = it 
                showError = false
            },
            placeholder = { Text("Enter 4-digit PIN", fontFamily = FontFamily.Cursive) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            shape = SketchyShapeAlt,
            isError = showError,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Black,
                unfocusedBorderColor = Color.Black
            )
        )
        
        if (showError) {
            Text("Incorrect PIN!", color = Color.Red, fontFamily = FontFamily.Cursive, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        SketchyButton(onClick = { 
            if (enteredPin == expectedPin) {
                onUnlock()
            } else {
                showError = true
                enteredPin = ""
            }
        }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("UNLOCK", fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Cursive)
        }
    }
}

@Composable
fun CensorAppUI(settingsManager: SettingsManager) {
    val context = LocalContext.current
    
    var badWords by remember { mutableStateOf(settingsManager.getBadWords().toList()) }
    var newWordText by remember { mutableStateOf("") }
    var newPinText by remember { mutableStateOf("") }
    
    var isVisualOn by remember { mutableStateOf(settingsManager.isVisualCensorOn) }
    var isAudioOn by remember { mutableStateOf(settingsManager.isAudioBleeperOn) }
    var isImageOn by remember { mutableStateOf(settingsManager.isImageCensorOn) }
    var currentPin by remember { mutableStateOf(settingsManager.pinCode) }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(context, CensorService::class.java).apply {
                putExtra("RESULT_CODE", result.resultCode)
                putExtra("RESULT_DATA", result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    // Step 1 of the permission flow: Ask for microphone to bleep out audio!
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        // Step 2: Now ask for Screen Recording permission
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    val infiniteTransition = rememberInfiniteTransition(label = "breathe")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.01f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOut), RepeatMode.Reverse),
        label = "scale"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            shape = SketchyShape,
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(3.dp, Color.Black),
            modifier = Modifier.fillMaxWidth().scale(scale),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Censor App", fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Cursive)
                    Text("See no evil. Hear no evil.", fontSize = 14.sp, fontFamily = FontFamily.Cursive, color = Color.DarkGray)
                }

                SketchyToggleRow("Visual Censor", "Black boxes over bad words", isVisualOn) { 
                    isVisualOn = it; settingsManager.isVisualCensorOn = it 
                }
                SketchyToggleRow("Audio Bleeper", "Bleep out spoken words", isAudioOn) { 
                    isAudioOn = it; settingsManager.isAudioBleeperOn = it 
                }
                SketchyToggleRow("Disturbing Images", "Blur NSFW & violence", isImageOn) { 
                    isImageOn = it; settingsManager.isImageCensorOn = it 
                }

                if (currentPin == null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = newPinText,
                            onValueChange = { if (it.length <= 4) newPinText = it },
                            placeholder = { Text("Set 4-Digit PIN...", fontFamily = FontFamily.Cursive) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = SketchyShapeAlt,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Black, unfocusedBorderColor = Color.Black)
                        )
                        SketchyButton(onClick = { 
                            if (newPinText.length == 4) {
                                settingsManager.pinCode = newPinText
                                currentPin = newPinText
                            }
                        }) {
                            Text("LOCK", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                } else {
                    Text("🔒 App is PIN Protected", fontFamily = FontFamily.Cursive, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Words to Block:", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Cursive)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (word in badWords) {
                            SketchyTag(word) {
                                settingsManager.removeBadWord(word)
                                badWords = settingsManager.getBadWords().toList()
                            }
                        }
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = newWordText,
                            onValueChange = { newWordText = it },
                            placeholder = { Text("Add a word...", fontFamily = FontFamily.Cursive) },
                            shape = SketchyShapeAlt,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Black, unfocusedBorderColor = Color.Black)
                        )
                        SketchyButton(onClick = { 
                            if (newWordText.isNotBlank()) {
                                settingsManager.addBadWord(newWordText)
                                badWords = settingsManager.getBadWords().toList()
                                newWordText = ""
                            }
                        }) {
                            Text("+", fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        }
                    }
                }

                SketchyButton(
                    onClick = {
                        if (!Settings.canDrawOverlays(context)) {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                        } else {
                            // Turn on Accessibility Text Censor
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            
                            // Check if Audio Recording permission is needed for the Bleeper
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                ) {
                    Text("START CENSORING", fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Cursive)
                }
            }
        }
    }
}

@Composable
fun SketchyToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, null) { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = FontFamily.Cursive)
            Text(subtitle, fontSize = 12.sp, fontFamily = FontFamily.Cursive, color = Color.DarkGray)
        }
        Switch(
            checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = Color.Black,
                uncheckedThumbColor = Color.Black, uncheckedTrackColor = Color.White,
                uncheckedBorderColor = Color.Black
            )
        )
    }
}

@Composable
fun SketchyTag(word: String, onRemove: () -> Unit) {
    Surface(shape = SketchyShapeAlt, border = BorderStroke(2.dp, Color.Black), color = Color.Transparent) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(word, fontFamily = FontFamily.Cursive)
            Spacer(Modifier.width(8.dp))
            Text("x", fontWeight = FontWeight.Bold, color = Color.Red, modifier = Modifier.clickable { onRemove() })
        }
    }
}

@Composable
fun SketchyButton(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val offset by animateFloatAsState(targetValue = if (isPressed) 6f else 0f, label = "press")
    val elevation by animateFloatAsState(targetValue = if (isPressed) 0f else 8f, label = "elevation")

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = SketchyShape,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = elevation.dp, pressedElevation = 0.dp),
        modifier = modifier.offset(x = offset.dp, y = offset.dp)
    ) {
        content()
    }
}