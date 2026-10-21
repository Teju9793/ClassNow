package com.teju.classnow.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teju.classnow.domain.timetable.ScheduleState
import com.teju.classnow.domain.timetable.TimetableStatus
import com.teju.classnow.ui.permissions.PermissionsReadinessDialog
import com.teju.classnow.ui.theme.CyanAccent
import com.teju.classnow.ui.viewmodel.TimetableViewModel
import com.teju.classnow.util.NotificationHelper
import com.teju.classnow.util.PermissionHelper
import com.teju.classnow.util.SmartSilentManager
import com.teju.classnow.util.TimeUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: TimetableViewModel,
    onManageTimetable: () -> Unit,
    onOpenSettings: () -> Unit = {},
    initialShowPermissions: Boolean = false,
    onPermissionsHandled: () -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshCurrentTime()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val scheduleState by viewModel.scheduleState.collectAsStateWithLifecycle()
    val currentTime by viewModel.currentTime.collectAsStateWithLifecycle()
    val isNotifyEnabled by viewModel.isLiveNotificationEnabled.collectAsStateWithLifecycle()
    val isSmartSilentEnabled by viewModel.isSmartSilentModeEnabled.collectAsStateWithLifecycle()
    val smartSilentTarget by viewModel.smartSilentTarget.collectAsStateWithLifecycle()
    val manualOverridePeriod by viewModel.manualOverridePeriod.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val hasSeenOnboarding by viewModel.hasSeenOnboarding.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showPermissionsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(initialShowPermissions) {
        if (initialShowPermissions) {
            showPermissionsDialog = true
            onPermissionsHandled()
        }
    }

    LaunchedEffect(hasSeenOnboarding) {
        if (hasSeenOnboarding == false) {
            val allGranted = PermissionHelper.hasDndPermission(context) &&
                    PermissionHelper.hasExactAlarmPermission(context) &&
                    PermissionHelper.hasNotificationPermission(context)
            if (allGranted) {
                viewModel.setHasSeenOnboarding(true)
            } else {
                showPermissionsDialog = true
            }
        }
    }

    val hasDndPermission = remember(isSmartSilentEnabled) {
        SmartSilentManager.hasDndPermission(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleLiveNotification(true)
        }
    }

    val todayFormatted = remember(currentTime) {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.US))
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "ClassNow",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (!isNotifyEnabled) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.toggleLiveNotification(true)
                            }
                        } else {
                            viewModel.toggleLiveNotification(false)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Toggle Notification",
                            tint = if (isNotifyEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }

                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Time & Date Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = TimeUtils.formatLocalTimeTo12Hour(currentTime),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = todayFormatted,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Primary Timetable Status Card
            HeroStatusCard(
                state = scheduleState,
                isSmartSilentEnabled = isSmartSilentEnabled,
                manualOverridePeriod = manualOverridePeriod,
                onToggleOverride = { periodKey ->
                    viewModel.toggleManualSoundOverride(periodKey)
                }
            )

            // Next Class Preview Card
            scheduleState.nextClass?.let { next ->
                if (scheduleState.status == TimetableStatus.CURRENT_CLASS ||
                    scheduleState.status == TimetableStatus.BREAK ||
                    scheduleState.status == TimetableStatus.LUNCH ||
                    scheduleState.status == TimetableStatus.FREE_PERIOD ||
                    scheduleState.status == TimetableStatus.BEFORE_CLASSES
                ) {
                    NextClassCard(next.subject, next.startTime, next.faculty)
                }
            }

            // Smart Silent Mode Status / Quick Control Card
            SmartSilentQuickCard(
                isEnabled = isSmartSilentEnabled,
                hasDndPermission = hasDndPermission,
                targetMode = smartSilentTarget,
                onToggle = { viewModel.toggleSmartSilentMode(it) },
                onRequestPermission = {
                    showPermissionsDialog = true
                }
            )

            Spacer(modifier = Modifier.weight(1f, fill = false))

            // Action: Manage Timetable
            Button(
                onClick = onManageTimetable,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    "Manage Timetable",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            themeMode = themeMode,
            isNotifyEnabled = isNotifyEnabled,
            isSmartSilentEnabled = isSmartSilentEnabled,
            smartSilentTarget = smartSilentTarget,
            onThemeChange = { viewModel.setThemeMode(it) },
            onNotifyChange = { viewModel.toggleLiveNotification(it) },
            onSilentModeChange = { viewModel.toggleSmartSilentMode(it) },
            onSilentTargetChange = { viewModel.setSmartSilentTarget(it) },
            onOpenPermissions = {
                showSettingsDialog = false
                showPermissionsDialog = true
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showPermissionsDialog) {
        PermissionsReadinessDialog(
            onDismiss = {
                showPermissionsDialog = false
                viewModel.setHasSeenOnboarding(true)
            },
            onPermissionChanged = {
                viewModel.triggerAllUpdates()
            }
        )
    }

    LaunchedEffect(Unit) {
        NotificationHelper.createNotificationChannel(context)
    }
}

@Composable
fun HeroStatusCard(
    state: ScheduleState,
    isSmartSilentEnabled: Boolean,
    manualOverridePeriod: String,
    onToggleOverride: (String) -> Unit
) {
    val isOverridden = state.periodKey.isNotEmpty() && state.periodKey == manualOverridePeriod

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (state.status) {
                TimetableStatus.CURRENT_CLASS -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        border = BorderStroke(
            1.dp,
            if (state.status == TimetableStatus.CURRENT_CLASS) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Pill Badge
            Surface(
                shape = RoundedCornerShape(50),
                color = when (state.status) {
                    TimetableStatus.CURRENT_CLASS -> MaterialTheme.colorScheme.primary
                    TimetableStatus.BREAK, TimetableStatus.FREE_PERIOD -> MaterialTheme.colorScheme.secondary
                    TimetableStatus.LUNCH -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = when (state.status) {
                    TimetableStatus.CURRENT_CLASS -> MaterialTheme.colorScheme.onPrimary
                    TimetableStatus.BREAK, TimetableStatus.FREE_PERIOD -> MaterialTheme.colorScheme.onSecondary
                    TimetableStatus.LUNCH -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            ) {
                Text(
                    text = when (state.status) {
                        TimetableStatus.CURRENT_CLASS -> "ACTIVE CLASS"
                        TimetableStatus.BREAK -> "SHORT BREAK"
                        TimetableStatus.LUNCH -> "LUNCH BREAK"
                        TimetableStatus.FREE_PERIOD -> "FREE PERIOD"
                        TimetableStatus.BEFORE_CLASSES -> "BEFORE CLASSES"
                        TimetableStatus.NO_MORE_CLASSES -> "CLASSES FINISHED"
                        TimetableStatus.NO_CLASSES_TODAY -> "NO CLASSES"
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Period Title
            Text(
                text = when (state.status) {
                    TimetableStatus.CURRENT_CLASS -> state.currentClass?.subject ?: "Class"
                    TimetableStatus.BREAK -> "Short Break"
                    TimetableStatus.LUNCH -> "Lunch Break"
                    TimetableStatus.FREE_PERIOD -> "Free Period"
                    TimetableStatus.BEFORE_CLASSES -> "Classes Haven't Started"
                    TimetableStatus.NO_MORE_CLASSES -> "Done for Today"
                    TimetableStatus.NO_CLASSES_TODAY -> "No Schedule Today"
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            // Subtitle / Timestamps
            when (state.status) {
                TimetableStatus.CURRENT_CLASS -> {
                    state.currentClass?.let { current ->
                        Text(
                            text = "${TimeUtils.formatTo12Hour(current.startTime)} — ${TimeUtils.formatTo12Hour(current.endTime)}${current.faculty?.let { " • $it" } ?: ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress Bar
                    LinearProgressIndicator(
                        progress = { state.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${state.progressPercentage}% completed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = state.timeSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Manual Sound Override Button for active class
                    if (isSmartSilentEnabled) {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = { onToggleOverride(state.periodKey) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = if (isOverridden) "Sound Unmuted (Manual) • Re-Mute" else "Silent Mode Active • Unmute",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
                TimetableStatus.BREAK,
                TimetableStatus.LUNCH,
                TimetableStatus.FREE_PERIOD -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.timeSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TimetableStatus.BEFORE_CLASSES -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.timeSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TimetableStatus.NO_MORE_CLASSES -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "All scheduled periods complete. Relax and recharge!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                TimetableStatus.NO_CLASSES_TODAY -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "No classes scheduled for today.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun NextClassCard(subject: String, startTime: String, faculty: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "NEXT CLASS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subject,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Starts at ${TimeUtils.formatTo12Hour(startTime)}${faculty?.let { " • $it" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SmartSilentQuickCard(
    isEnabled: Boolean,
    hasDndPermission: Boolean,
    targetMode: String,
    onToggle: (Boolean) -> Unit,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Smart Silent Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isEnabled) "Automating $targetMode during college hours" else "Turn on to silence phone in class",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle
                )
            }

            if (isEnabled && !hasDndPermission) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "DND access required to change sound mode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = onRequestPermission,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("Grant", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    themeMode: String,
    isNotifyEnabled: Boolean,
    isSmartSilentEnabled: Boolean,
    smartSilentTarget: String,
    onThemeChange: (String) -> Unit,
    onNotifyChange: (Boolean) -> Unit,
    onSilentModeChange: (Boolean) -> Unit,
    onSilentTargetChange: (String) -> Unit,
    onOpenPermissions: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Preferences", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Theme Mode Selector
                Column {
                    Text("Appearance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark").forEach { (mode, label) ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { onThemeChange(mode) },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Smart Silent Mode Settings
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Smart Silent Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Switch(
                            checked = isSmartSilentEnabled,
                            onCheckedChange = onSilentModeChange
                        )
                    }

                    if (isSmartSilentEnabled) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Silence Method:", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("SILENT" to "Total Mute", "VIBRATE" to "Vibrate Only").forEach { (target, label) ->
                                FilterChip(
                                    selected = smartSilentTarget == target,
                                    onClick = { onSilentTargetChange(target) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Live Notification
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Live Notification", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Keep persistent status in notification shade", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isNotifyEnabled,
                        onCheckedChange = onNotifyChange
                    )
                }

                HorizontalDivider()

                // Feature Readiness & Permissions
                OutlinedButton(
                    onClick = onOpenPermissions,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Permissions & Reliability Check")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
