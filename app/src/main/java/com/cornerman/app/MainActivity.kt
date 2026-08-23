package com.cornerman.app

import android.content.Context
import android.media.MediaMetadataRetriever
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.edit
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cornerman.app.data.*
import com.cornerman.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class Screen {
    data object Home : Screen()
    data object Analyze : Screen()
    data object History : Screen()
    data object Settings : Screen()
    data object Loading : Screen()
    
    // CornerMap Flow
    data object MapGameSelect : Screen()
    data class MapMapSelect(val game: GameDefinition) : Screen()
    data class MapTimeline(val game: GameDefinition, val map: MapDefinition) : Screen()
    
    data class Result(
        val result: CoachResult, 
        val type: String = "QUICK_IGL", 
        val isDemo: Boolean = false,
        val map: MapDefinition? = null
    ) : Screen()
    data class Failure(val message: String) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CornermanTheme {
                Surface(color = BgNearBlack) { CornermanApp() }
            }
        }
    }
}

data class HardwareStats(
    val temp: Float,
    val ramAvailable: Float,
    val batteryLevel: Int
)

@Composable
fun hardwareMonitor(): HardwareStats {
    val context = LocalContext.current
    var stats by remember { mutableStateOf(HardwareStats(0f, 0f, 0)) }
    
    LaunchedEffect(Unit) {
        while (true) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(memoryInfo)
            
            // Battery temperature is in tenths of a degree Celsius
            val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val temp = (intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
            
            stats = HardwareStats(
                temp = temp,
                ramAvailable = memoryInfo.availMem / (1024f * 1024f * 1024f),
                batteryLevel = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            )
            delay(5000) // Poll every 5 seconds
        }
    }
    return stats
}

@Composable
fun CornermanApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var apiKey by remember { mutableStateOf(SecurePrefs.loadApiKey(context)) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var storyboardState by remember { mutableStateOf<List<Bitmap>?>(null) }
    var mediaContextState by remember { mutableStateOf<SanitizedMediaContext?>(null) }
    var game by remember { mutableStateOf("BGMI") }
    var situation by remember { mutableStateOf("Death") }
    var note by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(HistoryStore.load(context)) }
    


    fun startAnalysis(manualMode: Boolean = false, manualMistake: String = "", manualVerdict: String = "") {
        val selected = bitmap ?: return
        
        screen = Screen.Loading
        scope.launch {
            if (manualMode) {
                delay(2000) // Cinematic delay for offline mode
                val localError = validateImageLocally(selected)
                if (localError != null) {
                    screen = Screen.Result(
                        CoachResult(
                            validGameScreen = false,
                            confidence = 0.0,
                            decisionScore = 0,
                            rootMistake = "Invalid",
                            whyYouDied = "",
                            biggestMistake = "",
                            betterPlay = "",
                            evidence = emptyList(),
                            nextFightRules = emptyList(),
                            iglVerdict = "",
                            rejectionReason = localError
                        ),
                        "QUICK_IGL"
                    )
                    return@launch
                }

                val result = CoachResult(
                    validGameScreen = true,
                    confidence = 1.0,
                    decisionScore = 70, 
                    rootMistake = manualMistake,
                    whyYouDied = "Manual self-review: $note",
                    biggestMistake = "Identified via self-coaching.",
                    betterPlay = "Follow your manual verdict.",
                    evidence = listOf("Self-reported evidence"),
                    nextFightRules = listOf("Trust your own analysis.", "Stay disciplined."),
                    iglVerdict = manualVerdict.ifBlank { "Stay focused." }
                )
                HistoryStore.add(context, game, situation, result, "QUICK_IGL")
                history = HistoryStore.load(context)
                screen = Screen.Result(result, "QUICK_IGL")
                return@launch
            }

            if (apiKey.isBlank()) {
                screen = Screen.Failure("Add your OpenRouter API key in Settings first.")
                return@launch
            }
            
            runCatching {
                OpenRouterApi.analyze(apiKey, selected, game, situation, note, mediaContextState, storyboardState)
            }.onSuccess { result ->
                if (result.validGameScreen) {
                    HistoryStore.add(context, game, situation, result, "QUICK_IGL")
                    history = HistoryStore.load(context)
                }
                screen = Screen.Result(result, "QUICK_IGL")
            }.onFailure { error ->
                screen = Screen.Failure(error.message ?: "The IGL could not complete the review.")
            }
        }
    }

    fun startMapAnalysis(finalTimeline: MatchTimeline, mapDef: MapDefinition) {
        if (apiKey.isBlank()) {
            screen = Screen.Failure("Add your OpenRouter API key in Settings first.")
            return
        }
        screen = Screen.Loading
        scope.launch {
            runCatching {
                OpenRouterApi.analyzeMap(apiKey, finalTimeline)
            }.onSuccess { result ->
                HistoryStore.add(context, finalTimeline.gameId, "CornerMap", result, "CORNERMAP")
                history = HistoryStore.load(context)
                screen = Screen.Result(result, "CORNERMAP", map = mapDef)
            }.onFailure { error ->
                screen = Screen.Failure(error.message ?: "CornerMap analysis failed.")
            }
        }
    }

    val hardware = hardwareMonitor()

    when (val current = screen) {
        Screen.Home -> HomeScreen(
            history = history,
            hasApiKey = apiKey.isNotBlank(),
            hardware = hardware,
            onAnalyze = { screen = Screen.Analyze },
            onCornerMap = { screen = Screen.MapGameSelect },
            onHistory = { history = HistoryStore.load(context); screen = Screen.History },
            onSettings = { screen = Screen.Settings }
        )
        Screen.MapGameSelect -> MapGameSelectScreen(
            onGameSelected = { gameObj -> screen = Screen.MapMapSelect(gameObj) },
            onBack = { screen = Screen.Home }
        )
        is Screen.MapMapSelect -> MapMapSelectScreen(
            game = current.game,
            onMapSelected = { mapObj -> screen = Screen.MapTimeline(current.game, mapObj) },
            onBack = { screen = Screen.MapGameSelect }
        )
        is Screen.MapTimeline -> CornerMapTimelineScreen(
            game = current.game,
            map = current.map,
            onSubmit = { startMapAnalysis(it, current.map) },
            onDemo = {
                screen = Screen.Result(
                    CoachResult(
                        validGameScreen = true,
                        confidence = 1.0,
                        decisionScore = 74,
                        rootMistake = "Rotation",
                        whyYouDied = "You rotated through the open field near Rozhok without enough information on the ridges.",
                        biggestMistake = "Prioritizing speed over cover during the mid-game rotation.",
                        betterPlay = "Take the Gatka ridge line. It offers superior high ground and visibility before committing to the lower urban areas.",
                        evidence = listOf("Open terrain markers", "Late zone entry", "Low high-ground usage"),
                        nextFightRules = listOf("High ground first.", "Scout ridges before urban entry.", "Rotate early to secure the edge."),
                        iglVerdict = "Stop rotating through death traps.",
                        yourPlaySummary = "Dropped Pochinki, looted fast, then drove straight through the Rozhok valley during Phase 3.",
                        cornermanRecommends = "Secure the Gatka ridge first. Use the natural terrain to scout the valley before moving.",
                        nextGamePlan = "Focus on ridge-to-ridge rotations. Avoid the low-ground death traps in mid-game."
                    ),
                    "CORNERMAP",
                    true,
                    map = current.map
                )
            },
            onBack = { screen = Screen.MapMapSelect(current.game) }
        )
        Screen.Analyze -> AnalyzeScreen(
            selectedBitmap = bitmap,
            game = game,
            situation = situation,
            note = note,
            mediaContext = mediaContextState,
            onGame = { game = it },
            onSituation = { situation = it },
            onNote = { note = it },
            onMediaPicked = { b, context, story -> 
                bitmap = b
                mediaContextState = context
                storyboardState = story
            },
            onBack = { screen = Screen.Home },
            onDemo = {
                screen = Screen.Result(
                    CoachResult(
                        validGameScreen = true,
                        confidence = 0.94,
                        decisionScore = 62,
                        rootMistake = "Exposure",
                        whyYouDied = "You committed to the angle while only partially protected. The opponent could see more of your body than you could see of theirs.",
                        biggestMistake = "You took the fight before converting your partial cover into a stronger angle.",
                        betterPlay = "Reset behind hard cover, gather information, then re-peek from a tighter angle instead of wide-swinging the same line.",
                        evidence = listOf("Partial cover is visible", "Enemy is visible on the same engagement line", "Player is exposed beyond the cover edge"),
                        nextFightRules = listOf("Cover first. Damage second.", "Don't re-peek the same angle twice.", "If the angle is neutral, create an advantage before swinging."),
                        iglVerdict = "Stop giving them the angle for free."
                    ),
                    "QUICK_IGL",
                    true
                )
            },
            onSubmit = { manual, mistake, verdict -> startAnalysis(manual, mistake, verdict) }
        )
        Screen.History -> HistoryScreen(
            history = history,
            onBack = { screen = Screen.Home },
            onAnalyze = { screen = Screen.Analyze }
        )
        Screen.Settings -> SettingsScreen(
            initialKey = apiKey,
            onSave = { key ->
                SecurePrefs.saveApiKey(context, key.trim())
                apiKey = key.trim()
                screen = Screen.Home
            },
            onClear = {
                SecurePrefs.clear(context)
                apiKey = ""
            },
            onBack = { screen = Screen.Home }
        )
        Screen.Loading -> LoadingScreen()
        is Screen.Result -> ResultScreen(
            result = current.result,
            type = current.type,
            isDemo = current.isDemo,
            mapDef = current.map,
            onAnalyzeAnother = { bitmap = null; screen = Screen.Analyze },
            onHome = { bitmap = null; screen = Screen.Home }
        )
        is Screen.Failure -> FailureScreen(
            message = current.message,
            onRetry = { screen = Screen.Analyze },
            onHome = { screen = Screen.Home }
        )
    }
}

@Composable
private fun HomeScreen(
    history: List<ReviewSummary>,
    hasApiKey: Boolean,
    hardware: HardwareStats,
    onAnalyze: () -> Unit,
    onCornerMap: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit
) {
    val average = if (history.isEmpty()) 0 else history.map { it.score }.average().toInt()
    val topMistake = history.groupingBy { it.mistake }.eachCount().maxByOrNull { it.value }?.key ?: "None yet"
    val trend = if (history.size >= 2) {
        val recent = history.take(5).map { it.score }.average()
        val older = history.drop(5).take(5).map { it.score }.average()
        if (older > 0) (recent - older).toInt() else 0
    } else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        CornermanMark(64.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            "CORNERMAN",
            color = TextPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 6.sp
        )
        Text(
            "YOUR AI IN-GAME LEADER",
            color = VioletLight,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.alpha(0.8f)
        )
        Spacer(Modifier.height(40.dp))

        // Hardware Tactical HUD
        HardwareHUDSection(hardware)
        Spacer(Modifier.height(24.dp))

        // Hero Metric: Decision Score
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(BgCard, BgNearBlack)))
                .border(1.dp, Brush.verticalGradient(listOf(VioletCore.copy(0.3f), Color.Transparent)), RoundedCornerShape(24.dp))
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "DECISION SCORE",
                    color = VioletLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (history.isEmpty()) "--" else "$average",
                    color = if (history.isEmpty()) TextSecondary else scoreColor(average),
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "/ 100",
                    color = TextSecondary.copy(0.5f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Metrics Grid
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("REVIEWS", "${history.size}", "Total fights", Modifier.weight(1f))
            StatCard("TREND", if (trend >= 0) "+$trend" else "$trend", "Recent diff", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        InfoCard(
            "BIGGEST WEAKNESS",
            topMistake,
            if (history.isEmpty()) "Analyze a fight to start coaching." else "Most frequent decision error"
        )

        Spacer(Modifier.height(32.dp))

        // Primary Action
        Button(
            onClick = onAnalyze,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(12.dp, RoundedCornerShape(20.dp), spotColor = VioletCore),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VioletCore)
        ) {
            Icon(Icons.Rounded.CenterFocusStrong, null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text("QUICK IGL REVIEW", fontWeight = FontWeight.Black, letterSpacing = 1.sp, fontSize = 16.sp)
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onCornerMap,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .border(1.dp, VioletCore.copy(0.5f), RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BgCard)
        ) {
            Icon(Icons.Rounded.Map, null, tint = VioletLight, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text("CORNERMAP PLANNER", color = TextPrimary, fontWeight = FontWeight.Black, letterSpacing = 1.sp, fontSize = 16.sp)
        }

        Spacer(Modifier.height(12.dp))

        // Secondary Navigation
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(onClick = onHistory, icon = Icons.Rounded.History, text = "Reviews", modifier = Modifier.weight(1f))
            SecondaryButton(onClick = onSettings, icon = Icons.Rounded.Settings, text = "Settings", modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(24.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (hasApiKey) SuccessGreen else Color.Gray))
            Spacer(Modifier.width(8.dp))
            Text(
                if (hasApiKey) "OPENROUTER • READY" else "OPENROUTER • API KEY REQUIRED",
                color = if (hasApiKey) SuccessGreen else TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }
        
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun HardwareHUDSection(stats: HardwareStats) {
    val isHot = stats.temp >= 40f
    val lowRam = stats.ramAvailable < 1.5f
    
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .border(1.dp, BgCardBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(
            "LIVE TACTICAL HUD",
            color = VioletLight,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(12.dp))
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HUDStat("TEMP", "${stats.temp}°C", if (stats.temp >= 43f) DangerRed else if (isHot) GoldAccent else SuccessGreen)
            HUDStat("RAM", "${"%.1f".format(stats.ramAvailable)}GB", if (lowRam) DangerRed else SuccessGreen)
            HUDStat("BATTERY", "${stats.batteryLevel}%", if (stats.batteryLevel < 20) DangerRed else SuccessGreen)
        }
        
        if (isHot || lowRam) {
            Spacer(Modifier.height(16.dp))
            Surface(
                color = DangerRed.copy(0.1f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DangerRed.copy(0.3f))
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Warning, null, tint = DangerRed, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isHot) "THERMAL RISK: Device heat may cause frame drops." else "RAM RISK: Close background apps for stability.",
                        color = DangerRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Spacer(Modifier.height(16.dp))
            Text(
                "SYSTEM OPTIMIZED: Device is ready for peak performance.",
                color = SuccessGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.alpha(0.8f)
            )
        }
    }
}

@Composable
private fun HUDStat(label: String, value: String, color: Color) {
    Column {
        Text(label, color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SecondaryButton(onClick: () -> Unit, icon: ImageVector, text: String, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(18.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BgCardBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = TextPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun AnalyzeScreen(
    selectedBitmap: Bitmap?, game: String, situation: String, note: String,
    mediaContext: SanitizedMediaContext?,
    onGame: (String) -> Unit, onSituation: (String) -> Unit, onNote: (String) -> Unit,
    onMediaPicked: (Bitmap, SanitizedMediaContext, List<Bitmap>?) -> Unit, 
    onBack: () -> Unit, onDemo: () -> Unit, onSubmit: (Boolean, String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var manualMode by remember { mutableStateOf(false) }
    var manualMistake by remember { mutableStateOf("Positioning") }
    var manualVerdict by remember { mutableStateOf("") }
    
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { 
            scope.launch {
                val b = runCatching { loadBitmapFromUri(context, it) }.getOrNull()
                val m = MediaContextExtractor.extract(context, it)
                val isVideo = m.mediaType == "video"
                val storyboard = if (isVideo) VideoProcessor.extractStoryboard(context, it) else null
                
                b?.let { onMediaPicked(it, m, storyboard) }
            }
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        TopBar("New IGL Review", onBack)
        Spacer(Modifier.height(8.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
            Text(
                "Every screenshot is an evidence-first lesson.",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            
            // Manual Toggle
            Text("MANUAL", color = if (manualMode) VioletLight else TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Switch(
                checked = manualMode,
                onCheckedChange = { manualMode = it },
                colors = SwitchDefaults.colors(checkedThumbColor = VioletCore, checkedTrackColor = VioletCore.copy(0.3f))
            )
        }
        
        Spacer(Modifier.height(24.dp))

        SectionHeader("01", "GAME")
        ChoiceRow(listOf("BGMI", "CODM BR", "Free Fire", "Other"), game, onGame)
        
        Spacer(Modifier.height(24.dp))
        SectionHeader("02", "SITUATION")
        ChoiceRow(listOf("Death", "Fight", "Positioning", "Rotation"), situation, onSituation)
        
        Spacer(Modifier.height(24.dp))
        SectionHeader("03", "GAMEPLAY EVIDENCE")
        Spacer(Modifier.height(12.dp))
        
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(24.dp))
                .background(BgCard)
                .clickable { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
                .border(1.dp, if (selectedBitmap == null) BgCardBorder else VioletCore.copy(0.4f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (selectedBitmap != null) {
                Image(
                    selectedBitmap.asImageBitmap(),
                    "Selected screenshot",
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Overlay to change
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Refresh, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
                
                // Media Metadata Badge
                mediaContext?.let {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("${it.width}x${it.height} • ${it.orientation.uppercase()}${if (it.mediaType == "video") " • VIDEO" else ""}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.AddAPhoto,
                        null,
                        tint = VioletCore,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Select a fight screenshot", color = TextSecondary, fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        
        if (manualMode) {
            SectionHeader("04", "SELF-COACHING")
            Spacer(Modifier.height(12.dp))
            Text("ROOT MISTAKE", color = VioletLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            ChoiceRow(listOf("Positioning", "Exposure", "Timing", "Aim"), manualMistake, { manualMistake = it })
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = manualVerdict,
                onValueChange = { manualVerdict = it },
                label = { Text("What's the rule for next time?") },
                placeholder = { Text("e.g. Always keep hard cover on my left") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            )
        } else {
            SectionHeader("04", "PLAYER MENTAL MODEL")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = onNote,
                placeholder = { Text("What did you think would happen?", color = TextSecondary.copy(0.5f)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 3,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VioletCore,
                    unfocusedBorderColor = BgCardBorder,
                    focusedContainerColor = BgCard.copy(0.5f),
                    unfocusedContainerColor = BgCard.copy(0.5f)
                )
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "CORNERMAN compares your model against the evidence.",
                color = TextSecondary.copy(0.6f),
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        Spacer(Modifier.height(32.dp))
        
        Button(
            onClick = { onSubmit(manualMode, manualMistake, manualVerdict) },
            enabled = selectedBitmap != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VioletCore,
                disabledContainerColor = BgCard
            )
        ) {
            Text(if (manualMode) "SAVE MANUAL REVIEW" else "RUN IGL REVIEW", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
        
        Spacer(Modifier.height(12.dp))
        
        TextButton(
            onClick = onDemo,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Rounded.PlayCircle, null, tint = VioletLight, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Demo Mode — no API needed", color = VioletLight, fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SectionHeader(num: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            num,
            color = VioletCore,
            fontWeight = FontWeight.Black,
            fontSize = 10.sp,
            modifier = Modifier
                .border(1.dp, VioletCore.copy(0.3f), CircleShape)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            color = TextPrimary,
            fontWeight = FontWeight.Black,
            fontSize = 12.sp,
            letterSpacing = 1.5.sp
        )
    }
}

@Composable
private fun ChoiceRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val chosen = option == selected
            Surface(
                onClick = { onSelect(option) },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (chosen) VioletCore.copy(alpha = 0.15f) else BgCard,
                border = BorderStroke(1.dp, if (chosen) VioletCore else BgCardBorder)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        option,
                        color = if (chosen) VioletLight else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (chosen) FontWeight.Black else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(initialKey: String, onSave: (String) -> Unit, onClear: () -> Unit, onBack: () -> Unit) {
    var key by remember { mutableStateOf(initialKey) }
    var keyVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    Column(Modifier.fillMaxSize().background(BgNearBlack).padding(24.dp)) {
        TopBar("Settings", onBack)
        Spacer(Modifier.height(20.dp))
        
        Text("OPENROUTER API KEY", color = VioletLight, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(8.dp))
        Text("Key is encrypted with Android Keystore.", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = key, onValueChange = { key = it }, singleLine = true,
            label = { Text("sk-or-v1-…") }, 
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        if (keyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (keyVisible) "Hide API Key" else "Show API Key",
                        tint = VioletLight
                    )
                }
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onSave(key) }, enabled = key.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = VioletCore)) { Text("Save API Key", fontWeight = FontWeight.Bold) }
        
        Spacer(Modifier.height(32.dp))
        Text("DANGER ZONE", color = DangerRed, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(12.dp))
        
        OutlinedButton(
            onClick = { onClear(); key = "" },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, DangerRed.copy(0.3f))
        ) {
            Text("Clear API Key", color = DangerRed)
        }
        
        Spacer(Modifier.height(12.dp))
        
        // Clear history button
        OutlinedButton(
            onClick = { 
                context.getSharedPreferences("cornerman_history", Context.MODE_PRIVATE).edit(commit = true) { clear() }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.Gray.copy(0.3f))
        ) {
            Text("Clear Review History", color = TextSecondary)
        }

        Spacer(Modifier.height(40.dp))
        InfoCard("VERSION", "1.0.0-MVP", "Built for the hackathon by CORNERMAN Team.")
    }
}

@Composable
private fun HistoryScreen(history: List<ReviewSummary>, onBack: () -> Unit, onAnalyze: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(BgNearBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        TopBar("Decision Memory", onBack)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your tactical patterns over ${history.size} reviews.",
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
        Spacer(Modifier.height(24.dp))

        if (history.isEmpty()) {
            EmptyHistory(onAnalyze)
        } else {
            // Tactical Profile
            TacticalProfile(history)
            
            Spacer(Modifier.height(24.dp))
            Text(
                "RECENT REVIEWS",
                color = TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(12.dp))
            
            history.forEach { review ->
                ReviewCard(review)
                Spacer(Modifier.height(12.dp))
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun TacticalProfile(history: List<ReviewSummary>) {
    val counts = history.groupingBy { it.mistake }.eachCount().toList().sortedByDescending { it.second }
    val top = counts.firstOrNull()
    
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BgCard)
            .padding(24.dp)
    ) {
        Text(
            "YOUR TACTICAL PROFILE",
            color = VioletLight,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(16.dp))
        
        counts.take(4).forEach { (mistake, count) ->
            val progress = count.toFloat() / history.size
            Column(Modifier.padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(mistake, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("$count", color = VioletLight, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(BgCardBorder)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(if (mistake == top?.first) VioletCore else VioletLight.copy(0.6f))
                    )
                }
            }
        }
        
        if (top != null && top.second > 1) {
            Spacer(Modifier.height(16.dp))
            Surface(
                color = VioletCore.copy(0.1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "${top.first} is your most repeated decision error.",
                    color = VioletLight,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ReviewCard(review: ReviewSummary) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BgCardBorder)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (review.type == "CORNERMAP") "CORNERMAP" else review.game,
                    color = TextPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "•",
                    color = TextSecondary.copy(0.3f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    review.situation,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${review.score}",
                    color = scoreColor(review.score),
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )
                Text(
                    "/100",
                    color = TextSecondary.copy(0.4f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                review.mistake.uppercase(),
                color = VioletLight,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                review.verdict,
                color = TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyHistory(onAnalyze: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.HistoryEdu,
            null,
            tint = BgCardBorder,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "NO TACTICAL PROFILE YET",
            color = TextPrimary,
            fontWeight = FontWeight.Black,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Analyze your first fight to reveal your patterns.",
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onAnalyze,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VioletCore)
        ) {
            Text("ANALYZE FIRST FIGHT", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LoadingScreen() {
    var stage by remember { mutableIntStateOf(0) }
    val stages = listOf(
        "OBSERVING THE FIGHT",
        "IDENTIFYING DECISIONS",
        "EVALUATING ANGLE",
        "COACHING THE PLAY"
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            stage = (stage + 1) % stages.size
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgNearBlack)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Tactical Crosshair Animation
        Box(contentAlignment = Alignment.Center) {
            val infiniteTransition = rememberInfiniteTransition(label = "loading")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
                label = "rotation"
            )
            
            Box(
                Modifier
                    .size(120.dp)
                    .graphicsLayer { rotationZ = rotation }
                    .drawBehind {
                        drawCircle(
                            VioletCore.copy(0.1f),
                            radius = size.width / 2f
                        )
                        drawArc(
                            VioletCore,
                            startAngle = 0f,
                            sweepAngle = 90f,
                            useCenter = false,
                            style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
                            topLeft = Offset.Zero,
                            size = size
                        )
                        drawArc(
                            VioletCore,
                            startAngle = 180f,
                            sweepAngle = 90f,
                            useCenter = false,
                            style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
                            topLeft = Offset.Zero,
                            size = size
                        )
                    }
            )
            CornermanMark(60.dp)
        }

        Spacer(Modifier.height(48.dp))
        
        Text(
            "CORNERMAN IS ANALYZING",
            color = TextPrimary,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            letterSpacing = 2.sp
        )
        
        Spacer(Modifier.height(16.dp))
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            stages.forEachIndexed { index, text ->
                val active = stage == index
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .alpha(if (active) 1f else 0.3f)
                        .padding(vertical = 4.dp)
                ) {
                    if (active) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(VioletCore)
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        text,
                        color = if (active) VioletLight else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        letterSpacing = 1.2.sp
                    )
                }
            }
        }
        
        Spacer(Modifier.height(48.dp))
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(2.dp)
                .clip(CircleShape),
            color = VioletCore,
            trackColor = BgCardBorder
        )
    }
}

@Composable
private fun ResultScreen(
    result: CoachResult, 
    type: String = "QUICK_IGL", 
    isDemo: Boolean = false,
    mapDef: MapDefinition? = null,
    onAnalyzeAnother: () -> Unit, 
    onHome: () -> Unit
) {
    if (type == "QUICK_IGL" && !result.validGameScreen) {
        // ... (existing rejection logic)
        Column(
            Modifier
                .fillMaxSize()
                .background(BgNearBlack)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(DangerRed.copy(0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Warning, null, tint = DangerRed, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "INSUFFICIENT EVIDENCE",
                color = TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                result.rejectionReason ?: "CORNERMAN needs a clearer gameplay screenshot to give a tactical verdict.",
                color = TextSecondary,
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onAnalyzeAnother,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VioletCore),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("TRY ANOTHER SCREENSHOT", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onHome, modifier = Modifier.padding(top = 8.dp)) {
                Text("Return to Dashboard", color = TextSecondary)
            }
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgNearBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        TopBar(if (type == "CORNERMAP") "Map Report" else "IGL Verdict", onHome)
        
        if (isDemo) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                color = GoldAccent.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.3f))
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PlayCircle, null, tint = GoldAccent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("DEMO MODE — NO API KEY USED", color = GoldAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))

        // Map Visualization for CornerMap
        if (type == "CORNERMAP") {
            BoxWithConstraints(
                Modifier.fillMaxWidth().height(220.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(BgCard)
                    .border(1.dp, BgCardBorder, RoundedCornerShape(24.dp))
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    
                    // Topographical contours
                    val topoColor = VioletCore.copy(alpha = 0.05f)
                    drawCircle(topoColor, radius = w * 0.3f, center = Offset(w * 0.4f, h * 0.5f), style = Stroke(2f))
                    drawCircle(topoColor, radius = w * 0.15f, center = Offset(w * 0.8f, h * 0.3f), style = Stroke(2f))
                    
                    // Your Play Path (Dashed White)
                    val yourPath = Path().apply {
                        moveTo(w * 0.2f, h * 0.8f)
                        lineTo(w * 0.5f, h * 0.7f)
                        lineTo(w * 0.8f, h * 0.3f)
                    }
                    drawPath(yourPath, Color.White.copy(0.2f), style = Stroke(4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
                    
                    // Recommended Path (Violet)
                    val recPath = Path().apply {
                        moveTo(w * 0.2f, h * 0.8f)
                        quadraticTo(w * 0.3f, h * 0.3f, w * 0.8f, h * 0.3f)
                    }
                    drawPath(recPath, VioletCore, style = Stroke(6f))
                    
                    // Markers
                    drawCircle(SuccessGreen, 10f, Offset(w * 0.2f, h * 0.8f)) // Drop
                    drawCircle(DangerRed, 14f, Offset(w * 0.8f, h * 0.3f)) // Loss
                }
                
                // POI Labels on Result Map
                mapDef?.pois?.forEach { poi ->
                    Text(
                        poi.name.uppercase(),
                        color = TextSecondary.copy(0.3f),
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.offset(x = this.maxWidth * poi.x - 20.dp, y = this.maxHeight * poi.y - 5.dp)
                    )
                }
                
                Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                    LegendItem(SuccessGreen, "Landing")
                    LegendItem(GoldAccent, "Last Zone")
                    LegendItem(VioletCore, "Recommended")
                    LegendItem(DangerRed, "Fight/Loss")
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Media Context Badge for Results
        result.rejectionReason?.let {
            // Rejection already handled
        } ?: run {
            Surface(
                color = BgCard,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BgCardBorder),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, null, tint = VioletLight, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Metadata context included in analysis.", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
        
        // Hero Score & Mistake
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ScoreCard(result.decisionScore, Modifier.weight(1f))
            Column(
                Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
            ) {
                InfoCard(
                    "ROOT MISTAKE",
                    result.rootMistake.uppercase(),
                    "Primary decision error",
                    Modifier.fillMaxSize()
                )
            }
        }
        
        Spacer(Modifier.height(12.dp))

        if (type == "CORNERMAP") {
            VerdictSection(
                label = "YOUR PLAY",
                icon = Icons.Rounded.Person,
                body = result.yourPlaySummary ?: "",
                accent = TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            VerdictSection(
                label = "CORNERMAN RECOMMENDS",
                icon = Icons.Rounded.AutoAwesome,
                body = result.cornermanRecommends ?: "",
                accent = VioletLight
            )
            Spacer(Modifier.height(10.dp))
            VerdictSection(
                label = "NEXT GAME PLAN",
                icon = Icons.AutoMirrored.Rounded.Assignment,
                body = result.nextGamePlan ?: "",
                accent = SuccessGreen
            )
        } else {
            VerdictSection(
                label = "WHY YOU DIED",
                icon = Icons.Rounded.Close,
                body = result.whyYouDied,
                accent = DangerRed
            )
            Spacer(Modifier.height(10.dp))
            VerdictSection(
                label = "BIGGEST DECISION ERROR",
                icon = Icons.Rounded.PriorityHigh,
                body = result.biggestMistake,
                accent = GoldAccent
            )
            Spacer(Modifier.height(10.dp))
            VerdictSection(
                label = "BETTER PLAY",
                icon = Icons.Rounded.CheckCircle,
                body = result.betterPlay,
                accent = SuccessGreen
            )
        }

        Spacer(Modifier.height(16.dp))
        
        // Evidence List
        EvidenceList(if (type == "CORNERMAP") listOf("Drop quality", "Rotation risk", "Fight selection") else result.evidence)
        
        Spacer(Modifier.height(12.dp))
        
        // Tactical Rules
        TacticalRules(result.nextFightRules)
        
        Spacer(Modifier.height(16.dp))
        
        // The Signature IGL Call
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(VioletDeep, VioletCore)))
                .padding(24.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Campaign, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "IGL CALL",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "“${result.iglVerdict.uppercase()}”",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 28.sp
                )
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        Button(
            onClick = onAnalyzeAnother,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VioletCore)
        ) {
            Text(if (type == "CORNERMAP") "NEW MAP REVIEW" else "ANALYZE ANOTHER", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
        
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) {
            Text("SAVE & RETURN TO DASHBOARD", color = TextSecondary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun VerdictSection(label: String, icon: ImageVector, body: String, accent: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .border(1.dp, BgCardBorder, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                color = VioletLight,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            body,
            color = TextPrimary,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EvidenceList(items: List<String>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .padding(20.dp)
    ) {
        Text(
            "VISIBLE EVIDENCE",
            color = VioletLight,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(12.dp))
        items.take(4).forEach { item ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Visibility, null, tint = VioletCore, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(12.dp))
                Text(item, color = TextSecondary, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun TacticalRules(rules: List<String>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .padding(20.dp)
    ) {
        Text(
            "NEXT-FIGHT RULES",
            color = VioletLight,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(12.dp))
        rules.take(3).forEachIndexed { index, rule ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    "${index + 1}",
                    color = VioletCore,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 1.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(rule, color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun ScoreCard(score: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(BgCard).border(1.dp, VioletCore.copy(alpha = .25f), RoundedCornerShape(20.dp)).padding(20.dp)) {
        Text("DECISION SCORE", color = VioletLight, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(8.dp))
        Text("$score", color = scoreColor(score), fontSize = 36.sp, fontWeight = FontWeight.Black)
        Text("/ 100", color = TextSecondary.copy(0.4f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatCard(label: String, value: String, caption: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .border(1.dp, BgCardBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(
            label,
            color = VioletLight,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            caption,
            color = TextSecondary,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun InfoCard(label: String, value: String, caption: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .border(1.dp, BgCardBorder, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Text(
            label,
            color = VioletLight,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            caption,
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }
}

private fun scoreColor(score: Int): Color = when {
    score >= 80 -> SuccessGreen
    score >= 60 -> VioletLight
    score >= 40 -> GoldAccent
    else -> DangerRed
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(40.dp)
                .background(BgCard, CircleShape)
                .border(1.dp, BgCardBorder, CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(
            title,
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun CornermanMark(size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val center = Offset(w / 2f, w / 2f)
        val t = w * 0.12f
        val brush = Brush.linearGradient(listOf(VioletLight, VioletCore, VioletDeep))
        
        // The "C" Crosshair / Corner Mark
        val path = Path().apply {
            moveTo(w * 0.8f, w * 0.2f)
            lineTo(w * 0.4f, w * 0.2f)
            quadraticTo(w * 0.2f, w * 0.2f, w * 0.2f, w * 0.4f)
            lineTo(w * 0.2f, w * 0.6f)
            quadraticTo(w * 0.2f, w * 0.8f, w * 0.4f, w * 0.8f)
            lineTo(w * 0.8f, w * 0.8f)
        }
        
        drawPath(
            path = path,
            brush = brush,
            style = Stroke(t, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        
        // Center Dot
        drawCircle(VioletLight, radius = w * 0.06f, center = center)
        
        // Crosshair lines
        val lineLen = w * 0.2f
        drawLine(Color.White.copy(0.6f), Offset(center.x - lineLen, center.y), Offset(center.x + lineLen, center.y), w * 0.03f, cap = StrokeCap.Round)
        drawLine(Color.White.copy(0.6f), Offset(center.x, center.y - lineLen), Offset(center.x, center.y + lineLen), w * 0.03f, cap = StrokeCap.Round)
    }
}

@Composable
private fun MapGameSelectScreen(onGameSelected: (GameDefinition) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(BgNearBlack).padding(20.dp)) {
        TopBar("Select Game", onBack)
        Spacer(Modifier.height(24.dp))
        GameData.Games.forEach { game ->
            Surface(
                onClick = { onGameSelected(game) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                color = BgCard,
                border = BorderStroke(1.dp, BgCardBorder)
            ) {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(VioletDeep), contentAlignment = Alignment.Center) {
                        Text(game.name.take(1), color = Color.White, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(game.name, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Rounded.ChevronRight, null, tint = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun MapMapSelectScreen(game: GameDefinition, onMapSelected: (MapDefinition) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(BgNearBlack).padding(20.dp)) {
        TopBar("${game.name} Maps", onBack)
        Spacer(Modifier.height(24.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(game.maps) { map ->
                Surface(
                    onClick = { onMapSelected(map) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = BgCard,
                    border = BorderStroke(1.dp, BgCardBorder)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp)).background(VioletDeep.copy(0.1f)), contentAlignment = Alignment.Center) {
                            Text(map.name, color = VioletLight, fontWeight = FontWeight.Black, fontSize = 24.sp, modifier = Modifier.alpha(0.5f))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(map.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveMap(
    mapDef: MapDefinition,
    markers: List<MapMarker>,
    onTap: (Offset) -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset += offsetChange
    }

    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(BgCard)
            .border(1.dp, BgCardBorder, RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    val normalizedX = (tapOffset.x - offset.x) / (size.width * scale)
                    val normalizedY = (tapOffset.y - offset.y) / (size.height * scale)
                    onTap(Offset(normalizedX, normalizedY))
                }
            }
            .transformable(state = state)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        ) {
            // Real Map Asset
            if (mapDef.assetResId != null) {
                Image(
                    painter = painterResource(id = mapDef.assetResId),
                    contentDescription = "Tactical Map",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val topoColor = VioletCore.copy(alpha = 0.08f)
                    drawCircle(topoColor, radius = w * 0.25f, center = Offset(w * 0.3f, h * 0.4f), style = Stroke(2f))
                }
            }

            // Procedural POIs
            mapDef.pois.forEach { poi ->
                Box(
                    Modifier
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) {
                                placeable.placeRelative(
                                    (poi.x * constraints.maxWidth).toInt() - placeable.width / 2,
                                    (poi.y * constraints.maxHeight).toInt() - placeable.height / 2
                                )
                            }
                        }
                ) {
                    Text(
                        poi.name.uppercase(),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = (8 / scale).coerceAtLeast(4f).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.background(Color.Black.copy(0.4f), RoundedCornerShape(2.dp)).padding(horizontal = 2.dp)
                    )
                }
            }

            // Rotation Path
            if (markers.size > 1) {
                Canvas(Modifier.fillMaxSize()) {
                    for (i in 0 until markers.size - 1) {
                        val m1 = markers[i]
                        val m2 = markers[i+1]
                        drawLine(
                            color = Color.White.copy(alpha = 0.7f),
                            start = Offset(m1.x * size.width, m1.y * size.height),
                            end = Offset(m2.x * size.width, m2.y * size.height),
                            strokeWidth = 4f / scale,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                        )
                    }
                }
            }

            // Markers
            markers.forEachIndexed { index, marker ->
                Box(
                    Modifier
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) {
                                placeable.placeRelative(
                                    (marker.x * constraints.maxWidth).toInt() - placeable.width / 2,
                                    (marker.y * constraints.maxHeight).toInt() - placeable.height / 2
                                )
                            }
                        }
                        .size((32 / scale).coerceAtLeast(16f).dp)
                        .clip(CircleShape)
                        .background(when(marker.type) {
                            MarkerType.LANDING -> SuccessGreen
                            MarkerType.LAST_ZONE -> GoldAccent
                            MarkerType.FIGHT -> DangerRed
                            else -> VioletCore
                        })
                        .border(1.dp, Color.White, CircleShape)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("${index + 1}", color = Color.White, fontSize = (12 / scale).coerceAtLeast(6f).sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun CornerMapTimelineScreen(
    game: GameDefinition,
    map: MapDefinition,
    onSubmit: (MatchTimeline) -> Unit,
    onDemo: () -> Unit,
    onBack: () -> Unit
) {
    var markers by remember { mutableStateOf(listOf<MapMarker>()) }
    var dropReason by remember { mutableStateOf("") }
    var playerIntent by remember { mutableStateOf("") }
    var whatHappened by remember { mutableStateOf("") }
    var outcome by remember { mutableStateOf("") }
    var currentStep by remember { mutableIntStateOf(0) } 

    Column(Modifier.fillMaxSize().background(BgNearBlack)) {
        Box(Modifier.padding(horizontal = 20.dp)) {
            TopBar(if (currentStep == 0) "Mark Timeline" else "Tactical Context", onBack)
        }
        
        if (currentStep == 0) {
            Column(Modifier.weight(1f).padding(20.dp)) {
                Text("Pan to move • Pinch to zoom • Tap to mark.", color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    InteractiveMap(
                        mapDef = map,
                        markers = markers,
                        onTap = { normOffset ->
                            val type = when {
                                markers.none { it.type == MarkerType.LANDING } -> MarkerType.LANDING
                                markers.none { it.type == MarkerType.LAST_ZONE } -> MarkerType.LAST_ZONE
                                else -> MarkerType.FIGHT
                            }
                            markers = markers + MapMarker(normOffset.x, normOffset.y, type)
                        }
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { markers = emptyList() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = BgCardBorder)) { Text("Reset") }
                    Button(onClick = { if (markers.isNotEmpty()) currentStep = 1 }, modifier = Modifier.weight(1f), enabled = markers.isNotEmpty()) { Text("Next: Details") }
                }
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
                val landingPOI = markers.find { it.type == MarkerType.LANDING }?.let { m ->
                    map.pois.minByOrNull { (it.x - m.x) * (it.x - m.x) + (it.y - m.y) * (it.y - m.y) }?.name
                } ?: "Unknown"
                
                val lastZonePOI = markers.find { it.type == MarkerType.LAST_ZONE }?.let { m ->
                    map.pois.minByOrNull { (it.x - m.x) * (it.x - m.x) + (it.y - m.y) * (it.y - m.y) }?.name
                } ?: "Unknown"

                SectionHeader("01", "MATCH LOCATIONS")
                Spacer(Modifier.height(12.dp))
                InfoCard("LANDING", landingPOI, "Detected from marker")
                Spacer(Modifier.height(8.dp))
                InfoCard("LAST ZONE", lastZonePOI, "Detected from marker")
                
                Spacer(Modifier.height(24.dp))
                SectionHeader("02", "DROP LOGIC")
                ChoiceRow(listOf("Loot", "Early Fights", "Safe", "Habit"), dropReason, { dropReason = it })
                
                Spacer(Modifier.height(24.dp))
                SectionHeader("03", "PLAYER INTENT")
                ChoiceRow(listOf("Aggressive", "Passive", "Rotate Early", "Hold Edge"), playerIntent, { playerIntent = it })
                
                Spacer(Modifier.height(24.dp))
                SectionHeader("04", "MATCH CONTEXT")
                Spacer(Modifier.height(8.dp))
                Text("VEHICLE", color = VioletLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                ChoiceRow(listOf("No Vehicle", "Bike", "Car", "Uaz"), "No Vehicle", { }) 
                Spacer(Modifier.height(12.dp))
                Text("SQUAD", color = VioletLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                ChoiceRow(listOf("Solo", "Duo", "Squad"), "Squad", { })
                
                Spacer(Modifier.height(24.dp))
                SectionHeader("05", "MATCH STORY")
                OutlinedTextField(value = whatHappened, onValueChange = { whatHappened = it }, placeholder = { Text("Short recap of what happened...") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                
                Spacer(Modifier.height(16.dp))
                SectionHeader("06", "OUTCOME")
                ChoiceRow(listOf("Won", "Lost", "Wiped", "Retreated"), outcome, { outcome = it })
                
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = {
                        onSubmit(MatchTimeline(game.id, map.id, markers, landingPOI, lastZonePOI, dropReason, playerIntent, whatHappened, outcome))
                    },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(20.dp)
                ) { Text("RUN TACTICAL REVIEW", fontWeight = FontWeight.Black) }
                
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDemo, modifier = Modifier.fillMaxWidth()) {
                    Text("DEMO MODE — NO API NEEDED", color = VioletLight, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FailureScreen(message: String, onRetry: () -> Unit, onHome: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(BgNearBlack)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(DangerRed.copy(0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.CloudOff, null, tint = DangerRed, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "CONNECTION FAILED",
            color = TextPrimary,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            message,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VioletCore),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text("TRY AGAIN", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onHome, modifier = Modifier.padding(top = 8.dp)) {
            Text("Back to Dashboard", color = TextSecondary)
        }
    }
}

private fun validateImageLocally(bitmap: Bitmap): String? {
    if (bitmap.height > bitmap.width) {
        return "Portrait image detected. Battle Royale games are played in Landscape mode. Please upload a horizontal screenshot."
    }
    // Lowered minimum resolution to support more devices and cropped screenshots
    val minSide = 480 
    if (bitmap.width < minSide || bitmap.height < minSide) {
        return "Image resolution is too low ($ {bitmap.width}x${bitmap.height}). CORNERMAN needs a clearer view to analyze the UI and markers."
    }
    return null
}

private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap {
    val contentResolver = context.contentResolver
    val mimeType = contentResolver.getType(uri)
    if (mimeType?.startsWith("video") == true) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            return retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: throw RuntimeException("Failed to get video frame")
        } finally {
            retriever.release()
        }
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ -> decoder.isMutableRequired = false }
    } else {
        @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
}
