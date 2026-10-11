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
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

// --- PEBBLE SAGE DESIGN TOKENS ---
val PebbleForest = Color(0xFF0A6C57)
val PebbleMint = Color(0xFFE6F5F0)
val PebblePeach = Color(0xFFFFF1DB)
val PebbleLilac = Color(0xFFF3E8FF)
val PebbleBg = Color(0xFFFAFCFA)
val PebbleCard = Color(0xFFFFFFFF)
val PebbleText = Color(0xFF132E27)
val PebbleMuted = Color(0xFF5F7D74)
val PebbleBorder = Color(0xFFE5EEEB)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = PebbleBg) {
                    val context = LocalContext.current
                    val settingsManager = remember { SettingsManager(context) }

                    var currentScreen by remember { mutableStateOf("SHIELD") }
                    var hasCompletedOnboarding by remember { mutableStateOf(settingsManager.pinCode != null || settingsManager.getBadWords().isNotEmpty()) }

                    Crossfade(targetState = if (!hasCompletedOnboarding) "ONBOARDING" else currentScreen) { screen ->
                        when (screen) {
                            "ONBOARDING" -> PebbleOnboardingScreen { hasCompletedOnboarding = true }
                            "SHIELD" -> PebbleShieldDashboard(settingsManager, onNavigate = { currentScreen = it })
                            "BLACKLIST" -> PebbleBlacklistScreen(settingsManager, onNavigate = { currentScreen = it })
                            "INTERCEPT" -> PebbleInterceptScreen(onBack = { currentScreen = "SHIELD" })
                            "PARENT" -> PebbleParentScreen(settingsManager, onNavigate = { currentScreen = it })
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 1. ONBOARDING & SETUP SCREEN
// ==========================================
@Composable
fun PebbleOnboardingScreen(onFinish: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Logo Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Icon(Icons.Default.Star, contentDescription = "Pebble", tint = PebbleForest, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("pebble", fontSize = 28.sp, fontWeight = FontWeight.Black, color = PebbleForest)
            Spacer(modifier = Modifier.width(8.dp))
            Surface(color = PebbleMint, shape = RoundedCornerShape(100)) {
                Text("CENSOR AI", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PebbleForest)
            }
        }

        // Mascot Card (Figma Style)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(230.dp)
                .background(PebbleMint, RoundedCornerShape(36.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.pebble_mascot),
                contentDescription = "Pebble Mascot",
                modifier = Modifier.size(160.dp)
            )
        }

        // Value Proposition
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Gentle Censorship.\nReal-Time Protection.", fontSize = 28.sp, fontWeight = FontWeight.Black, color = PebbleText, textAlign = TextAlign.Center, lineHeight = 34.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Pebble automatically blurs bad words, bleeps spoken profanity, and pixelates sensitive photos across all your apps.",
                fontSize = 14.sp, color = PebbleMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 3 Censorship Pillars
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = PebbleForest, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Text Blur", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PebbleForest)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Audio Bleep", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7C3AED))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Face, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Image Shield", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                }
            }
        }

        // Bottom CTA
        Column(modifier = Modifier.fillMaxWidth()) {
            Surface(color = PebbleMint, shape = RoundedCornerShape(100), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = PebbleForest, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("100% On-Device AI. Zero data leaves your phone.", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PebbleForest)
                }
            }

            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PebbleForest),
                shape = RoundedCornerShape(100)
            ) {
                Text("Enable Pebble Protection", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// ==========================================
// 2. LIVE SHIELD DASHBOARD
// ==========================================
@Composable
fun PebbleShieldDashboard(settingsManager: SettingsManager, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    var isShieldActive by remember { mutableStateOf(true) }
    var isVisualOn by remember { mutableStateOf(settingsManager.isVisualCensorOn) }
    var isAudioOn by remember { mutableStateOf(settingsManager.isAudioBleeperOn) }
    var isImageOn by remember { mutableStateOf(settingsManager.isImageCensorOn) }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(context, CensorService::class.java).apply {
                putExtra("RESULT_CODE", result.resultCode)
                putExtra("RESULT_DATA", result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
            else context.startService(serviceIntent)
            isShieldActive = true
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    Scaffold(
        bottomBar = {
            PebbleBottomNavigation(activeTab = "SHIELD", onNavigate = onNavigate)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("YOUR SAFE SPACE", fontSize = 11.sp, fontWeight = FontWeight.Black, color = PebbleMuted, letterSpacing = 1.sp)
                    Text("Hey, Alex!", fontSize = 28.sp, fontWeight = FontWeight.Black, color = PebbleText)
                }
                Box(
                    modifier = Modifier.size(42.dp).background(PebbleLilac, CircleShape).clickable { onNavigate("PARENT") },
                    contentAlignment = Alignment.Center
                ) {
                    Text("A", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7C3AED))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Master Protection Shield Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!Settings.canDrawOverlays(context)) {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                        } else {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
                            }
                        }
                    },
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = PebbleMint)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(44.dp).background(PebbleForest, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Protection Active", fontSize = 16.sp, fontWeight = FontWeight.Black, color = PebbleForest)
                                Text("Shielding screen & audio", fontSize = 12.sp, color = PebbleMuted)
                            }
                        }
                        Switch(
                            checked = isShieldActive,
                            onCheckedChange = { isShieldActive = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PebbleForest)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFFC8E8DD))
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Guarded Apps: YouTube • TikTok • Chrome • Instagram", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PebbleForest)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // AI Engines Control Group
            Text("ACTIVE AI ENGINES", fontSize = 11.sp, fontWeight = FontWeight.Black, color = PebbleMuted, letterSpacing = 0.5.sp)
            Spacer(modifier = Modifier.height(8.dp))

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = PebbleCard)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PebbleCensorEngineRow("Zero-Lag Text Blur", "Exact word bounds frosted glass", Icons.Default.Lock, PebbleForest, isVisualOn) {
                        isVisualOn = it; settingsManager.isVisualCensorOn = it
                    }
                    HorizontalDivider(color = PebbleBorder)
                    PebbleCensorEngineRow("Audio Bleeper Interceptor", "Vosk speech AI mutes video profanity", Icons.Default.PlayArrow, Color(0xFF7C3AED), isAudioOn) {
                        isAudioOn = it; settingsManager.isAudioBleeperOn = it
                    }
                    HorizontalDivider(color = PebbleBorder)
                    PebbleCensorEngineRow("Targeted Image Blocker", "Anti-blink pixelation on sensitive photos", Icons.Default.Face, Color(0xFFB45309), isImageOn) {
                        isImageOn = it; settingsManager.isImageCensorOn = it
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Today's Activity Peace of Mind
            Text("TODAY'S PROTECTION", fontSize = 11.sp, fontWeight = FontWeight.Black, color = PebbleMuted, letterSpacing = 0.5.sp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PebbleQuickStat("14", "Words softened", Icons.Default.Lock, PebbleForest, Modifier.weight(1f))
                PebbleQuickStat("3", "Audio bleeps", Icons.Default.PlayArrow, Color(0xFF7C3AED), Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Preview Intercept Screen Trigger
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onNavigate("INTERCEPT") },
                color = PebbleCard,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PebbleBorder)
            ) {
                Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = PebbleForest, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Preview Friendly Intercept Screen →", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PebbleMuted)
                }
            }
        }
    }
}

// ==========================================
// 3. BLACKLIST & WORD MANAGER SCREEN
// ==========================================
@Composable
fun PebbleBlacklistScreen(settingsManager: SettingsManager, onNavigate: (String) -> Unit) {
    var badWords by remember { mutableStateOf(settingsManager.getBadWords().toList()) }
    var newWordText by remember { mutableStateOf("") }

    Scaffold(
        bottomBar = {
            PebbleBottomNavigation(activeTab = "BLACKLIST", onNavigate = onNavigate)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("FILTER CONTROLS", fontSize = 11.sp, fontWeight = FontWeight.Black, color = PebbleMuted, letterSpacing = 1.sp)
                    Text("Blacklist Manager", fontSize = 28.sp, fontWeight = FontWeight.Black, color = PebbleText)
                }
                IconButton(onClick = { onNavigate("SHIELD") }) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = PebbleForest)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Words in this blacklist are blurred in real-time across all browser pages, chats, and social feeds.", fontSize = 13.sp, color = PebbleMuted)

            Spacer(modifier = Modifier.height(18.dp))

            // Add Word Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newWordText,
                    onValueChange = { newWordText = it },
                    placeholder = { Text("Add word to filter...", color = PebbleMuted) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PebbleForest, unfocusedBorderColor = PebbleBorder)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (newWordText.isNotBlank()) {
                            settingsManager.addBadWord(newWordText.trim().lowercase())
                            badWords = settingsManager.getBadWords().toList()
                            newWordText = ""
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PebbleForest),
                    modifier = Modifier.height(56.dp)
                ) {
                    Text("Add", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("ACTIVE FILTERED WORDS (${badWords.size})", fontSize = 11.sp, fontWeight = FontWeight.Black, color = PebbleMuted, letterSpacing = 0.5.sp)
            Spacer(modifier = Modifier.height(10.dp))

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = PebbleCard)) {
                Box(modifier = Modifier.padding(16.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(badWords) { word ->
                            Surface(
                                color = PebbleMint,
                                shape = RoundedCornerShape(100),
                                modifier = Modifier.clickable {
                                    settingsManager.removeBadWord(word)
                                    badWords = settingsManager.getBadWords().toList()
                                }
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(word, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PebbleForest)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = PebbleForest, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. FRIENDLY INTERCEPT SCREEN (IN-ACTION BLOCKED)
// ==========================================
@Composable
fun PebbleInterceptScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Reassurance Badge
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PebbleForest) }
            Surface(color = PebbleMint, shape = RoundedCornerShape(100)) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PebbleForest, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pebble has your back", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = PebbleForest)
                }
            }
            Spacer(modifier = Modifier.width(48.dp))
        }

        // Mascot
        Image(
            painter = painterResource(id = R.drawable.pebble_mascot),
            contentDescription = "Caring Pebble",
            modifier = Modifier.size(140.dp)
        )

        // Friendly Explanation
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Let's try a\nfriendlier place.", fontSize = 28.sp, fontWeight = FontWeight.Black, color = PebbleText, textAlign = TextAlign.Center, lineHeight = 34.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "This page isn't for your age group. You haven't done anything wrong — we're here to help you explore safely.",
                fontSize = 13.sp, color = PebbleMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Action Buttons
        Column(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PebbleForest),
                shape = RoundedCornerShape(100)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back to safety", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(100),
                border = androidx.compose.foundation.BorderStroke(2.dp, PebbleForest)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = PebbleForest)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ask a grown-up", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PebbleForest)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ==========================================
// 5. PARENTAL LOCK & PIN SCREEN
// ==========================================
@Composable
fun PebbleParentScreen(settingsManager: SettingsManager, onNavigate: (String) -> Unit) {
    var pinCode by remember { mutableStateOf(settingsManager.pinCode) }
    var newPinText by remember { mutableStateOf("") }

    Scaffold(
        bottomBar = {
            PebbleBottomNavigation(activeTab = "PARENT", onNavigate = onNavigate)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("PARENTAL INSIGHTS", fontSize = 11.sp, fontWeight = FontWeight.Black, color = PebbleMuted, letterSpacing = 1.sp)
                    Text("A safer space.", fontSize = 28.sp, fontWeight = FontWeight.Black, color = PebbleText)
                }
                IconButton(onClick = { onNavigate("SHIELD") }) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = PebbleForest)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // PIN Security Card
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = PebbleCard)) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(52.dp).background(PebbleMint, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = PebbleForest, modifier = Modifier.size(26.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (pinCode != null) "Settings Are Protected" else "Set 4-Digit Parent PIN",
                        fontSize = 17.sp, fontWeight = FontWeight.Black, color = PebbleText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Settings, blacklist changes, and service toggles are locked so children cannot disable protection.",
                        fontSize = 12.sp, color = PebbleMuted, textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = newPinText,
                        onValueChange = { if (it.length <= 4) newPinText = it },
                        placeholder = { Text("Enter 4-Digit PIN", color = PebbleMuted) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PebbleForest, unfocusedBorderColor = PebbleBorder)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (newPinText.length == 4) {
                                settingsManager.pinCode = newPinText
                                pinCode = newPinText
                                newPinText = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PebbleForest),
                        shape = RoundedCornerShape(100)
                    ) {
                        Text("Save Master PIN", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// REUSABLE COMPONENTS
// ==========================================
@Composable
fun PebbleCensorEngineRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, iconTint: Color, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.size(36.dp).background(iconTint.copy(alpha = 0.12f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PebbleText)
                Text(subtitle, fontSize = 11.sp, color = PebbleMuted)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PebbleForest)
        )
    }
}

@Composable
fun PebbleQuickStat(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, modifier: Modifier) {
    Surface(modifier = modifier, color = PebbleCard, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, PebbleBorder)) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(38.dp).background(tint.copy(alpha = 0.12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(value, fontSize = 18.sp, fontWeight = FontWeight.Black, color = PebbleText)
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PebbleMuted)
            }
        }
    }
}

@Composable
fun PebbleBottomNavigation(activeTab: String, onNavigate: (String) -> Unit) {
    Surface(color = Color.White, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PebbleBottomNavItem("Shield", Icons.Default.Lock, activeTab == "SHIELD") { onNavigate("SHIELD") }
            PebbleBottomNavItem("Blacklist", Icons.Default.Settings, activeTab == "BLACKLIST") { onNavigate("BLACKLIST") }
            PebbleBottomNavItem("Parents", Icons.Default.Lock, activeTab == "PARENT") { onNavigate("PARENT") }
        }
    }
}

@Composable
fun PebbleBottomNavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = if (active) PebbleForest else Color(0xFF8CA49D), modifier = Modifier.size(24.dp))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (active) PebbleForest else Color(0xFF8CA49D))
    }
}