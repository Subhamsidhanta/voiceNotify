package com.example

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * UI model representing an installed launchable application on the device.
 */
data class InstalledAppItem(
    val appName: String,
    val packageName: String,
    val iconBitmap: ImageBitmap?
)

/**
 * Navigation destination state for VoiceNotify.
 */
enum class VoiceNotifyScreen {
    HOME,
    VOICE_LOGS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                VoiceNotifyApp()
            }
        }
    }
}

@Composable
fun VoiceNotifyApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefManager = remember { PrefManager(context) }

    var currentScreen by rememberSaveable { mutableStateOf(VoiceNotifyScreen.HOME) }

    // Persistent states synced with PrefManager & Quick Settings Tile
    var isMasterEnabled by remember { mutableStateOf(prefManager.isMasterEnabled) }
    var isHeadphoneOnly by remember { mutableStateOf(prefManager.isHeadphoneOnly) }
    var speechRate by remember { mutableFloatStateOf(prefManager.speechRate) }
    var speechPitch by remember { mutableFloatStateOf(prefManager.speechPitch) }
    var selectedPackages by remember { mutableStateOf(prefManager.getSelectedApps()) }
    var lastAnnouncement by remember { mutableStateOf(prefManager.lastAnnouncementSummary) }
    var voiceLogs by remember { mutableStateOf(prefManager.getVoiceLogs()) }

    // System permission states
    var hasNotificationAccess by remember {
        mutableStateOf(isNotificationListenerGranted(context))
    }
    var isIgnoringBatteryOpt by remember {
        mutableStateOf(isBatteryOptimizationIgnored(context))
    }

    // Installed apps and filter states
    var installedApps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showVoiceTuning by rememberSaveable { mutableStateOf(true) }

    // Local TextToSpeech instance for the "Test Voice" and Voice Log replay buttons
    var previewTts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isPreviewTtsReady by remember { mutableStateOf(false) }

    val speakPhrase: (String) -> Unit = { rawPhrase ->
        // Always strip emojis and speak the total remaining text
        val cleanPhrase = NotificationReaderService.stripEmojisAndNormalize(rawPhrase)
        val engine = previewTts
        if (isPreviewTtsReady && engine != null && cleanPhrase.isNotBlank()) {
            engine.setSpeechRate(speechRate)
            engine.setPitch(speechPitch)
            engine.speak(
                cleanPhrase,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "test_${UUID.randomUUID()}"
            )
        } else {
            Toast.makeText(
                context,
                "Speaking: \"$cleanPhrase\"",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        try {
            engine = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val langRes = engine?.setLanguage(Locale.getDefault())
                    if (langRes == TextToSpeech.LANG_MISSING_DATA ||
                        langRes == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        engine?.setLanguage(Locale.US)
                    }
                    isPreviewTtsReady = true
                }
            }
            previewTts = engine
        } catch (_: Exception) {
        }

        val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                PrefManager.KEY_MASTER_ENABLED -> isMasterEnabled = prefManager.isMasterEnabled
                PrefManager.KEY_HEADPHONE_ONLY -> isHeadphoneOnly = prefManager.isHeadphoneOnly
                PrefManager.KEY_SELECTED_APPS -> selectedPackages = prefManager.getSelectedApps()
                PrefManager.KEY_SPEECH_RATE -> speechRate = prefManager.speechRate
                PrefManager.KEY_SPEECH_PITCH -> speechPitch = prefManager.speechPitch
                PrefManager.KEY_LAST_ANNOUNCEMENT ->
                    lastAnnouncement = prefManager.lastAnnouncementSummary
                PrefManager.KEY_VOICE_LOGS -> voiceLogs = prefManager.getVoiceLogs()
            }
        }
        prefManager.registerListener(prefListener)

        onDispose {
            prefManager.unregisterListener(prefListener)
            try {
                engine?.stop()
                engine?.shutdown()
            } catch (_: Exception) {
            }
        }
    }

    // Re-check permissions and logs automatically whenever the user returns from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationAccess = isNotificationListenerGranted(context)
                isIgnoringBatteryOpt = isBatteryOptimizationIgnored(context)
                isMasterEnabled = prefManager.isMasterEnabled
                selectedPackages = prefManager.getSelectedApps()
                lastAnnouncement = prefManager.lastAnnouncementSummary
                voiceLogs = prefManager.getVoiceLogs()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Load all user-installed/launchable apps asynchronously on Dispatchers.IO
    LaunchedEffect(Unit) {
        isLoadingApps = true
        val loadedApps = withContext(Dispatchers.IO) {
            loadLaunchableApps(context)
        }
        installedApps = loadedApps
        isLoadingApps = false
    }

    when (currentScreen) {
        VoiceNotifyScreen.HOME -> {
            HomeScreen(
                hasNotificationAccess = hasNotificationAccess,
                isIgnoringBatteryOpt = isIgnoringBatteryOpt,
                onGrantNotificationAccess = { openNotificationListenerSettings(context) },
                onRequestIgnoreBatteryOpt = { requestIgnoreBatteryOptimizations(context) },
                isMasterEnabled = isMasterEnabled,
                onMasterToggle = { enabled ->
                    isMasterEnabled = enabled
                    prefManager.isMasterEnabled = enabled
                },
                isHeadphoneOnly = isHeadphoneOnly,
                onHeadphoneToggle = { enabled ->
                    isHeadphoneOnly = enabled
                    prefManager.isHeadphoneOnly = enabled
                },
                selectedPackages = selectedPackages,
                lastAnnouncement = lastAnnouncement,
                voiceLogsCount = voiceLogs.size,
                onOpenVoiceLogsScreen = { currentScreen = VoiceNotifyScreen.VOICE_LOGS },
                showVoiceTuning = showVoiceTuning,
                onToggleVoiceTuning = { showVoiceTuning = !showVoiceTuning },
                speechRate = speechRate,
                onSpeechRateChange = { rate ->
                    speechRate = rate
                    prefManager.speechRate = rate
                },
                speechPitch = speechPitch,
                onSpeechPitchChange = { pitch ->
                    speechPitch = pitch
                    prefManager.speechPitch = pitch
                },
                onTestVoice = {
                    val sampleSenderRaw = "🔥 Rahul 🚀 (Work) ✨"
                    val cleanedSender =
                        NotificationReaderService.stripEmojisAndNormalize(sampleSenderRaw)
                    val sampleApp = "WhatsApp"
                    val cleanPhrase = "$sampleApp notification from $cleanedSender"

                    prefManager.appendVoiceLog(
                        appName = sampleApp,
                        senderName = cleanedSender,
                        spokenPhrase = cleanPhrase
                    )
                    voiceLogs = prefManager.getVoiceLogs()
                    lastAnnouncement = prefManager.lastAnnouncementSummary

                    speakPhrase(cleanPhrase)
                },
                installedApps = installedApps,
                isLoadingApps = isLoadingApps,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onSelectAllApps = {
                    val allPkgs = installedApps.map { it.packageName }.toSet()
                    selectedPackages = allPkgs
                    prefManager.setSelectedApps(allPkgs)
                },
                onDeselectAllApps = {
                    selectedPackages = emptySet()
                    prefManager.setSelectedApps(emptySet())
                },
                onToggleSingleApp = { packageName, checked ->
                    selectedPackages = prefManager.toggleAppSelection(
                        packageName = packageName,
                        isSelected = checked
                    )
                }
            )
        }

        VoiceNotifyScreen.VOICE_LOGS -> {
            VoiceLogsScreen(
                logs = voiceLogs,
                installedApps = installedApps,
                onBack = { currentScreen = VoiceNotifyScreen.HOME },
                onReplayEntry = { entry -> speakPhrase(entry.spokenPhrase) },
                onDeleteEntry = { logId ->
                    voiceLogs = prefManager.deleteVoiceLog(logId)
                    lastAnnouncement = prefManager.lastAnnouncementSummary
                },
                onDeleteLogsForApp = { appName ->
                    voiceLogs = prefManager.deleteVoiceLogsByApp(appName)
                    lastAnnouncement = prefManager.lastAnnouncementSummary
                },
                onClearAllLogs = {
                    prefManager.clearVoiceLogs()
                    voiceLogs = emptyList()
                    lastAnnouncement = ""
                },
                onAddSampleLog = {
                    val samples = listOf(
                        Triple("WhatsApp", "🔥 Rahul 🚀 (Work) ✨", System.currentTimeMillis()),
                        Triple(
                            "Telegram",
                            "🎉 Priya Sharma 💬",
                            System.currentTimeMillis() - 3_600_000L
                        ),
                        Triple(
                            "Gmail",
                            "📌 GitHub Security Alert 🔒",
                            System.currentTimeMillis() - 86_400_000L
                        )
                    )
                    val picked = samples.random()
                    val cleanSender =
                        NotificationReaderService.stripEmojisAndNormalize(picked.second)
                    val cleanPhrase = "${picked.first} notification from $cleanSender"
                    prefManager.appendVoiceLog(
                        appName = picked.first,
                        senderName = cleanSender,
                        spokenPhrase = cleanPhrase,
                        timestamp = picked.third
                    )
                    voiceLogs = prefManager.getVoiceLogs()
                    lastAnnouncement = prefManager.lastAnnouncementSummary
                    speakPhrase(cleanPhrase)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    hasNotificationAccess: Boolean,
    isIgnoringBatteryOpt: Boolean,
    onGrantNotificationAccess: () -> Unit,
    onRequestIgnoreBatteryOpt: () -> Unit,
    isMasterEnabled: Boolean,
    onMasterToggle: (Boolean) -> Unit,
    isHeadphoneOnly: Boolean,
    onHeadphoneToggle: (Boolean) -> Unit,
    selectedPackages: Set<String>,
    lastAnnouncement: String,
    voiceLogsCount: Int,
    onOpenVoiceLogsScreen: () -> Unit,
    showVoiceTuning: Boolean,
    onToggleVoiceTuning: () -> Unit,
    speechRate: Float,
    onSpeechRateChange: (Float) -> Unit,
    speechPitch: Float,
    onSpeechPitchChange: (Float) -> Unit,
    onTestVoice: () -> Unit,
    installedApps: List<InstalledAppItem>,
    isLoadingApps: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectAllApps: () -> Unit,
    onDeselectAllApps: () -> Unit,
    onToggleSingleApp: (String, Boolean) -> Unit
) {
    // Sort with Selected apps first, then alphabetical (A-Z), filtered by searchQuery
    val filteredAndSortedApps by remember(installedApps, selectedPackages, searchQuery) {
        derivedStateOf {
            val query = searchQuery.trim().lowercase(Locale.ROOT)
            installedApps
                .filter { app ->
                    query.isEmpty() ||
                        app.appName.lowercase(Locale.ROOT).contains(query) ||
                        app.packageName.lowercase(Locale.ROOT).contains(query)
                }
                .sortedWith(
                    compareByDescending<InstalledAppItem> { selectedPackages.contains(it.packageName) }
                        .thenBy { it.appName.lowercase(Locale.ROOT) }
                )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.privacy_badge_text),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .testTag("main_lazy_column"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Permission & Battery Optimization Status Banner
                item(key = "permission_status_section") {
                    PermissionManagementSection(
                        hasNotificationAccess = hasNotificationAccess,
                        isIgnoringBatteryOpt = isIgnoringBatteryOpt,
                        onGrantNotificationAccess = onGrantNotificationAccess,
                        onRequestIgnoreBatteryOpt = onRequestIgnoreBatteryOpt
                    )
                }

                // 2. High-Contrast Master Switch Card & Headphone-Only Toggle
                item(key = "master_controls_section") {
                    MasterAndHeadphoneCard(
                        isMasterEnabled = isMasterEnabled,
                        onMasterToggle = onMasterToggle,
                        isHeadphoneOnly = isHeadphoneOnly,
                        onHeadphoneToggle = onHeadphoneToggle,
                        selectedAppCount = selectedPackages.size,
                        lastAnnouncement = lastAnnouncement
                    )
                }

                // 3. Single Prominent Button to Open Dedicated Date-Wise Voice Logs Screen
                item(key = "voice_logs_navigation_button") {
                    VoiceLogsNavigationButton(
                        logsCount = voiceLogsCount,
                        lastAnnouncement = lastAnnouncement,
                        onClick = onOpenVoiceLogsScreen
                    )
                }

                // 4. Voice Tuning & Test Voice Announcement Card (Demonstrates Emoji Skipping)
                item(key = "voice_tuning_section") {
                    VoiceTuningCard(
                        expanded = showVoiceTuning,
                        onToggleExpand = onToggleVoiceTuning,
                        speechRate = speechRate,
                        onSpeechRateChange = onSpeechRateChange,
                        speechPitch = speechPitch,
                        onSpeechPitchChange = onSpeechPitchChange,
                        onTestVoice = onTestVoice
                    )
                }

                // 5. Universal App Selection Header, Search Bar, and Select/Deselect All
                item(key = "app_filter_header") {
                    AppSelectionFilterBar(
                        searchQuery = searchQuery,
                        onSearchQueryChange = onSearchQueryChange,
                        totalAppsCount = installedApps.size,
                        selectedAppsCount = selectedPackages.size,
                        onSelectAll = onSelectAllApps,
                        onDeselectAll = onDeselectAllApps
                    )
                }

                // 6. Installed Apps List
                if (isLoadingApps) {
                    item(key = "loading_indicator") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    modifier = Modifier.testTag("apps_loading_indicator")
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Loading installed applications…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (filteredAndSortedApps.isEmpty()) {
                    item(key = "empty_apps_state") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) {
                                        "No apps matching \"$searchQuery\""
                                    } else {
                                        "No launchable apps found on device"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = filteredAndSortedApps,
                        key = { it.packageName }
                    ) { appItem ->
                        val isSelected = selectedPackages.contains(appItem.packageName)
                        InstalledAppRowCard(
                            app = appItem,
                            isSelected = isSelected,
                            onToggle = { checked ->
                                onToggleSingleApp(appItem.packageName, checked)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single prominent button/card on the Home Screen that opens the full Voice Announcement Logs screen.
 */
@Composable
private fun VoiceLogsNavigationButton(
    logsCount: Int,
    lastAnnouncement: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("open_voice_logs_screen_button"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.open_voice_logs_btn),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Text(
                            text = logsCount.toString(),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
                Text(
                    text = if (lastAnnouncement.isNotBlank()) {
                        "Latest: $lastAnnouncement"
                    } else {
                        "View date-wise history, filter by app & delete logs"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open Voice Announcement Logs",
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * Dedicated full-screen Voice Announcement Logs screen featuring:
 * - Date-wise grouped sections (Today, Yesterday, or formatted date e.g. Oct 21, 2026)
 * - Search bar by App Name, Sender Name, or Spoken Phrase
 * - Quick App Filter chips so the user can view a particular app's logs with one tap
 * - Individual log deletion, per-app log deletion, and Delete All logs
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceLogsScreen(
    logs: List<VoiceLogEntry>,
    installedApps: List<InstalledAppItem>,
    onBack: () -> Unit,
    onReplayEntry: (VoiceLogEntry) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onDeleteLogsForApp: (String) -> Unit,
    onClearAllLogs: () -> Unit,
    onAddSampleLog: () -> Unit
) {
    // Mandatory BackHandler for secondary screen navigation
    BackHandler(onBack = onBack)

    var appSearchQuery by rememberSaveable { mutableStateOf("") }
    var selectedAppFilter by rememberSaveable { mutableStateOf<String?>(null) }

    // Map of appName -> iconBitmap for visual richness in log rows
    val appIconsByName = remember(installedApps) {
        installedApps.associate { it.appName.lowercase(Locale.ROOT) to it.iconBitmap }
    }

    // Distinct app names present in logs along with their log counts
    val distinctLoggedApps by remember(logs) {
        derivedStateOf {
            logs.groupBy { it.appName }
                .map { (appName, items) -> appName to items.size }
                .sortedByDescending { it.second }
        }
    }

    // Reset selectedAppFilter if all logs for that app were deleted
    LaunchedEffect(distinctLoggedApps) {
        if (selectedAppFilter != null &&
            distinctLoggedApps.none { it.first.equals(selectedAppFilter, ignoreCase = true) }
        ) {
            selectedAppFilter = null
        }
    }

    // Filter logs by search query (app name / sender name / spoken text) and selected app chip
    val filteredLogs by remember(logs, appSearchQuery, selectedAppFilter) {
        derivedStateOf {
            val query = appSearchQuery.trim().lowercase(Locale.ROOT)
            logs.filter { entry ->
                val matchesAppChip = selectedAppFilter == null ||
                    entry.appName.equals(selectedAppFilter, ignoreCase = true)
                val matchesQuery = query.isEmpty() ||
                    entry.appName.lowercase(Locale.ROOT).contains(query) ||
                    entry.senderName.lowercase(Locale.ROOT).contains(query) ||
                    entry.spokenPhrase.lowercase(Locale.ROOT).contains(query)
                matchesAppChip && matchesQuery
            }
        }
    }

    // Group filtered logs date-wise (preserving descending chronological order)
    val logsGroupedByDate by remember(filteredLogs) {
        derivedStateOf {
            filteredLogs.groupBy { entry ->
                formatDateSectionHeader(entry.timestamp)
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("voice_logs_screen"),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("voice_logs_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Home"
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.voice_log_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${filteredLogs.size} of ${logs.size} announcements • Date-wise",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (logs.isNotEmpty()) {
                        TextButton(
                            onClick = onClearAllLogs,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.testTag("delete_all_logs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = stringResource(R.string.clear_voice_log),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = stringResource(R.string.clear_voice_log))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .padding(horizontal = 16.dp)
            ) {
                // Search Bar for filtering by App Name or Sender Name
                OutlinedTextField(
                    value = appSearchQuery,
                    onValueChange = { appSearchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("search_voice_logs_input"),
                    placeholder = { Text(stringResource(R.string.voice_log_search_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search logs by app"
                        )
                    },
                    trailingIcon = {
                        if (appSearchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { appSearchQuery = "" },
                                modifier = Modifier.testTag("clear_log_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                // App Filter Chips Row ("All Apps" + each particular App present in logs)
                if (distinctLoggedApps.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        FilterChip(
                            selected = selectedAppFilter == null,
                            onClick = { selectedAppFilter = null },
                            label = { Text("All Apps (${logs.size})") },
                            modifier = Modifier.testTag("filter_chip_all_apps")
                        )
                        distinctLoggedApps.forEach { (appName, count) ->
                            val isSelected = selectedAppFilter.equals(appName, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedAppFilter = if (isSelected) null else appName
                                },
                                label = { Text("$appName ($count)") },
                                modifier = Modifier.testTag("filter_chip_app_$appName")
                            )
                        }
                    }
                }

                // Optional quick action bar when viewing a particular app's logs
                val activeApp = selectedAppFilter
                if (activeApp != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Showing logs for $activeApp (${filteredLogs.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            TextButton(
                                onClick = { onDeleteLogsForApp(activeApp) },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.testTag("delete_filtered_app_logs_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete $activeApp Logs")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Date-wise LazyColumn of logs
                if (filteredLogs.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                            .testTag("voice_logs_empty_card"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (appSearchQuery.isNotBlank() || selectedAppFilter != null) {
                                    "No logs match your current app filter"
                                } else {
                                    "No Voice Logs Recorded Yet"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.voice_log_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            FilledTonalButton(
                                onClick = onAddSampleLog,
                                modifier = Modifier.testTag("add_sample_log_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add & Speak Sample Log")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("voice_logs_lazy_column"),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        logsGroupedByDate.forEach { (dateHeader, entriesForDate) ->
                            item(key = "date_header_$dateHeader") {
                                DateSectionHeader(
                                    dateTitle = dateHeader,
                                    count = entriesForDate.size
                                )
                            }

                            items(
                                items = entriesForDate,
                                key = { it.id }
                            ) { entry ->
                                VoiceLogItemCard(
                                    entry = entry,
                                    appIcon = appIconsByName[entry.appName.lowercase(Locale.ROOT)],
                                    onFilterByThisApp = {
                                        selectedAppFilter = entry.appName
                                    },
                                    onReplay = { onReplayEntry(entry) },
                                    onDelete = { onDeleteEntry(entry.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateSectionHeader(dateTitle: String, count: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = dateTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = if (count == 1) "1 log" else "$count logs",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun VoiceLogItemCard(
    entry: VoiceLogEntry,
    appIcon: ImageBitmap?,
    onFilterByThisApp: () -> Unit,
    onReplay: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voice_log_item_${entry.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon or Monogram Avatar (clicking filters by this app)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onFilterByThisApp),
                contentAlignment = Alignment.Center
            ) {
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = "${entry.appName} icon",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = entry.appName.take(1).uppercase(Locale.ROOT),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable(onClick = onFilterByThisApp)
                    ) {
                        Text(
                            text = entry.appName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = entry.senderName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "\"${entry.spokenPhrase}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = entry.formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Replay Spoken Announcement Button
            IconButton(
                onClick = onReplay,
                modifier = Modifier.testTag("replay_log_${entry.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.VolumeUp,
                    contentDescription = "Replay voice announcement",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Delete Single Log Entry Button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_log_${entry.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete this log entry",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Formats a timestamp into a human-friendly Date section header ("Today — Oct 23, 2026",
 * "Yesterday — Oct 22, 2026", or "EEE, MMM d, yyyy").
 */
private fun formatDateSectionHeader(timestamp: Long): String {
    if (timestamp <= 0L) return "Unknown Date"
    val targetCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val todayCal = Calendar.getInstance()
    val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    val datePart = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    val dayOfWeekPart =
        SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))

    val isToday = targetCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
        targetCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)

    val isYesterday = targetCal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
        targetCal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)

    return when {
        isToday -> "Today — $datePart"
        isYesterday -> "Yesterday — $datePart"
        else -> dayOfWeekPart
    }
}

@Composable
private fun PermissionManagementSection(
    hasNotificationAccess: Boolean,
    isIgnoringBatteryOpt: Boolean,
    onGrantNotificationAccess: () -> Unit,
    onRequestIgnoreBatteryOpt: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!hasNotificationAccess) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("notification_permission_banner"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.permission_banner_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = stringResource(R.string.permission_banner_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onGrantNotificationAccess,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier.testTag("grant_notification_permission_button")
                    ) {
                        Text(text = stringResource(R.string.grant_permission_btn))
                    }
                }
            }
        }

        if (!isIgnoringBatteryOpt) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("battery_optimization_banner"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.BatteryChargingFull,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.battery_banner_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = stringResource(R.string.battery_banner_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    FilledTonalButton(
                        onClick = onRequestIgnoreBatteryOpt,
                        modifier = Modifier.testTag("ignore_battery_optimization_button")
                    ) {
                        Text(text = stringResource(R.string.ignore_battery_btn))
                    }
                }
            }
        }

        if (hasNotificationAccess && isIgnoringBatteryOpt) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("permissions_verified_badge"),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Notification Listener & Unrestricted Battery Active",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MasterAndHeadphoneCard(
    isMasterEnabled: Boolean,
    onMasterToggle: (Boolean) -> Unit,
    isHeadphoneOnly: Boolean,
    onHeadphoneToggle: (Boolean) -> Unit,
    selectedAppCount: Int,
    lastAnnouncement: String
) {
    val cardContainerColor by animateColorAsState(
        targetValue = if (isMasterEnabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "master_card_color"
    )

    val cardContentColor by animateColorAsState(
        targetValue = if (isMasterEnabled) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "master_content_color"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("master_switch_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardContainerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMasterToggle(!isMasterEnabled) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isMasterEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isMasterEnabled) {
                                Icons.Filled.NotificationsActive
                            } else {
                                Icons.Filled.VolumeOff
                            },
                            contentDescription = null,
                            tint = if (isMasterEnabled) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.master_switch_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = cardContentColor
                    )
                    Text(
                        text = if (isMasterEnabled) {
                            stringResource(R.string.master_switch_on_desc)
                        } else {
                            stringResource(R.string.master_switch_off_desc)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = cardContentColor.copy(alpha = 0.85f)
                    )
                }

                Switch(
                    checked = isMasterEnabled,
                    onCheckedChange = onMasterToggle,
                    modifier = Modifier.testTag("master_switch_toggle")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = cardContentColor.copy(alpha = 0.16f))
            Spacer(modifier = Modifier.height(14.dp))

            // Headphone-Only Mode Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onHeadphoneToggle(!isHeadphoneOnly) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Headphones,
                    contentDescription = null,
                    tint = cardContentColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.headphone_mode_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = cardContentColor
                    )
                    Text(
                        text = stringResource(R.string.headphone_mode_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = cardContentColor.copy(alpha = 0.8f)
                    )
                }
                Switch(
                    checked = isHeadphoneOnly,
                    onCheckedChange = onHeadphoneToggle,
                    modifier = Modifier.testTag("headphone_only_toggle")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Status summary pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$selectedAppCount apps enabled • 10s anti-spam cooldown",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = cardContentColor.copy(alpha = 0.9f)
                )
            }

            if (lastAnnouncement.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Last spoken: $lastAnnouncement",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = cardContentColor.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VoiceTuningCard(
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    speechRate: Float,
    onSpeechRateChange: (Float) -> Unit,
    speechPitch: Float,
    onSpeechPitchChange: (Float) -> Unit,
    onTestVoice: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voice_tuning_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Voice Tuning & Live Test",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "Rate: %.1fx • Pitch: %.1fx • Skips Emojis",
                                speechRate,
                                speechPitch
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = "Toggle voice controls"
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.speech_rate_label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = String.format(Locale.US, "%.2fx", speechRate),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = speechRate,
                        onValueChange = onSpeechRateChange,
                        valueRange = 0.5f..2.0f,
                        steps = 5,
                        modifier = Modifier.testTag("speech_rate_slider")
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.speech_pitch_label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = String.format(Locale.US, "%.2fx", speechPitch),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = speechPitch,
                        onValueChange = onSpeechPitchChange,
                        valueRange = 0.5f..2.0f,
                        steps = 5,
                        modifier = Modifier.testTag("speech_pitch_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onTestVoice,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_voice_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.test_voice_btn))
                        }

                        OutlinedButton(
                            onClick = {
                                onSpeechRateChange(1.0f)
                                onSpeechPitchChange(1.0f)
                            },
                            modifier = Modifier.testTag("reset_voice_button")
                        ) {
                            Text("Reset 1.0x")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppSelectionFilterBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    totalAppsCount: Int,
    selectedAppsCount: Int,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "App Announcements Filter",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Selected apps ($selectedAppsCount of $totalAppsCount) pinned to top",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_apps_input"),
            placeholder = { Text(stringResource(R.string.search_apps_hint)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search apps"
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.testTag("clear_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Clear search"
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = onSelectAll,
                modifier = Modifier
                    .weight(1f)
                    .testTag("select_all_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.DoneAll,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.select_all))
            }

            OutlinedButton(
                onClick = onDeselectAll,
                modifier = Modifier
                    .weight(1f)
                    .testTag("deselect_all_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.RemoveDone,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.deselect_all))
            }
        }
    }
}

@Composable
private fun InstalledAppRowCard(
    app: InstalledAppItem,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isSelected) }
            .testTag("app_item_${app.packageName}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (app.iconBitmap != null) {
                Image(
                    bitmap = app.iconBitmap,
                    contentDescription = "${app.appName} icon",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = app.appName.take(1).uppercase(Locale.ROOT),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Checkbox(
                checked = isSelected,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("app_checkbox_${app.packageName}")
            )
        }
    }
}

/**
 * Checks whether NotificationListenerService permission is enabled for this package.
 */
fun isNotificationListenerGranted(context: Context): Boolean {
    return try {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        enabledPackages.contains(context.packageName)
    } catch (_: Exception) {
        false
    }
}

/**
 * Checks whether the app is currently whitelisted from Android Doze / Battery Optimizations.
 */
fun isBatteryOptimizationIgnored(context: Context): Boolean {
    return try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    } catch (_: Exception) {
        false
    }
}

/**
 * Opens Android's Notification Listener Settings screen.
 */
fun openNotificationListenerSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(
            context,
            "Unable to open Notification Listener settings on this device.",
            Toast.LENGTH_SHORT
        ).show()
    }
}

/**
 * Launches the system dialog to ignore battery optimizations for VoiceNotify,
 * falling back to the general battery optimization settings screen if restricted.
 */
@Suppress(" BatteryLife")
fun requestIgnoreBatteryOptimizations(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        } catch (_: Exception) {
            Toast.makeText(
                context,
                "Unable to open Battery Optimization settings.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

/**
 * Queries PackageManager for all launchable/user-installed applications and converts their icons
 * to Compose ImageBitmaps for smooth LazyColumn scrolling.
 */
fun loadLaunchableApps(context: Context): List<InstalledAppItem> {
    val pm = context.packageManager
    val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(
            mainIntent,
            PackageManagerFlagsCompat.of(0L)
        )
    } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(mainIntent, 0)
    }

    val seenPackages = HashSet<String>()
    val result = ArrayList<InstalledAppItem>()

    for (resolveInfo in resolveInfos) {
        val activityInfo = resolveInfo.activityInfo ?: continue
        val pkgName = activityInfo.packageName ?: continue
        if (pkgName == context.packageName || !seenPackages.add(pkgName)) continue

        val label = resolveInfo.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkgName }
        val iconBitmap = try {
            drawableToImageBitmap(resolveInfo.loadIcon(pm))
        } catch (_: Exception) {
            null
        }

        result.add(
            InstalledAppItem(
                appName = label,
                packageName = pkgName,
                iconBitmap = iconBitmap
            )
        )
    }

    return result
}

private object PackageManagerFlagsCompat {
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun of(value: Long): android.content.pm.PackageManager.ResolveInfoFlags {
        return android.content.pm.PackageManager.ResolveInfoFlags.of(value)
    }
}

private fun drawableToImageBitmap(drawable: Drawable?, targetSizePx: Int = 96): ImageBitmap? {
    if (drawable == null) return null
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap.asImageBitmap()
    }
    val width = drawable.intrinsicWidth.coerceAtLeast(1).coerceAtMost(targetSizePx)
    val height = drawable.intrinsicHeight.coerceAtLeast(1).coerceAtMost(targetSizePx)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap.asImageBitmap()
}
