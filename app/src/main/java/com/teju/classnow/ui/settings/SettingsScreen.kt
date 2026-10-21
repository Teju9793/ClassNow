package com.teju.classnow.ui.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teju.classnow.BuildConfig
import com.teju.classnow.R
import com.teju.classnow.ui.permissions.PermissionsReadinessDialog
import com.teju.classnow.ui.viewmodel.TimetableViewModel
import com.teju.classnow.util.SmartSilentManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: TimetableViewModel,
    onBack: () -> Unit,
    onNavigatePrivacyPolicy: () -> Unit,
    onNavigateHelpDesk: () -> Unit,
    onNavigateDeveloperProfile: () -> Unit,
    onNavigateAbout: () -> Unit,
    onNavigateManageTimetable: () -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val isSmartSilentEnabled by viewModel.isSmartSilentModeEnabled.collectAsState()
    val smartSilentTarget by viewModel.smartSilentTarget.collectAsState()
    val isNotifyEnabled by viewModel.isLiveNotificationEnabled.collectAsState()

    val context = LocalContext.current
    var showPermissionsDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showSilentTargetDialog by remember { mutableStateOf(false) }

    val hasDndPermission = remember(isSmartSilentEnabled) {
        SmartSilentManager.hasDndPermission(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Group 1: Appearance & Theme
            SettingsSectionHeader(title = "Appearance")
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                SettingsClickableItem(
                    painter = rememberVectorPainter(Icons.Default.Settings),
                    title = "App Theme",
                    subtitle = when (themeMode) {
                        "LIGHT" -> "Light mode"
                        "DARK" -> "Dark mode (Neon Aqua)"
                        else -> "System default"
                    },
                    onClick = { showThemeDialog = true }
                )
            }

            // Group 2: Home Screen Widget
            SettingsSectionHeader(title = "Home Screen Widget")
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_schedule),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Dual-Class Live Widget",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Shows Current Class and Next Class side-by-side or stacked in 4×2 default size.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Tip: Long-press your home screen, select Widgets -> ClassNow to add it. Use the widget's refresh button anytime for instant updates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            // Group 3: Timetable & Data
            SettingsSectionHeader(title = "Timetable & Data")
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column {
                    SettingsClickableItem(
                        painter = painterResource(id = R.drawable.ic_schedule),
                        title = "Manage Timetable",
                        subtitle = "Add, edit, reorder classes, or import/export CSV",
                        onClick = onNavigateManageTimetable
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsSwitchItem(
                        painter = rememberVectorPainter(Icons.Default.Notifications),
                        title = "Persistent Notification",
                        subtitle = "Show ongoing timetable status in system notification shade",
                        checked = isNotifyEnabled,
                        onCheckedChange = { viewModel.toggleLiveNotification(it) }
                    )
                }
            }

            // Group 4: Smart Silent Mode
            SettingsSectionHeader(title = "Smart Silent Mode")
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column {
                    SettingsSwitchItem(
                        painter = painterResource(id = R.drawable.ic_volume_off),
                        title = "Master Smart Silent Switch",
                        subtitle = if (isSmartSilentEnabled) {
                            if (hasDndPermission) "Active: automatically mutes during classes" else "Enabled, but DND permission required"
                        } else "Disabled: phone sound remains manual",
                        checked = isSmartSilentEnabled,
                        onCheckedChange = { viewModel.toggleSmartSilentMode(it) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsClickableItem(
                        painter = painterResource(id = R.drawable.ic_volume_up),
                        title = "Mute Preference",
                        subtitle = if (smartSilentTarget == "VIBRATE") "Vibrate mode" else "Total Silence (DND)",
                        onClick = { showSilentTargetDialog = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsClickableItem(
                        painter = rememberVectorPainter(Icons.Default.Lock),
                        title = "Permissions Readiness Checklist",
                        subtitle = "Verify DND, Exact Alarms, and Notification permissions",
                        onClick = { showPermissionsDialog = true }
                    )
                }
            }

            // Group 5: Privacy, Help & Support
            SettingsSectionHeader(title = "Support & Privacy")
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column {
                    SettingsClickableItem(
                        painter = rememberVectorPainter(Icons.Default.Lock),
                        title = "Privacy Policy",
                        subtitle = "100% offline, local database, zero telemetry",
                        onClick = onNavigatePrivacyPolicy
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsClickableItem(
                        painter = rememberVectorPainter(Icons.Default.Info),
                        title = "Help Desk & FAQ",
                        subtitle = "Widget guides, troubleshooting, and direct support email",
                        onClick = onNavigateHelpDesk
                    )
                }
            }

            // Group 6: About
            SettingsSectionHeader(title = "About")
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column {
                    SettingsClickableItem(
                        painter = rememberVectorPainter(Icons.Default.Person),
                        title = "About the Developer",
                        subtitle = "K. R. Tejas Kumar • LinkedIn, Instagram & Contact",
                        onClick = onNavigateDeveloperProfile
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsClickableItem(
                        painter = rememberVectorPainter(Icons.Default.Info),
                        title = "About ClassNow",
                        subtitle = "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) • Open-source licenses",
                        onClick = onNavigateAbout
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Theme Picker Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose App Theme", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "SYSTEM" to "System default",
                        "LIGHT" to "Light theme",
                        "DARK" to "Dark theme (Neon Aqua)"
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (themeMode == mode),
                                onClick = {
                                    viewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Silent Target Picker Dialog
    if (showSilentTargetDialog) {
        AlertDialog(
            onDismissRequest = { showSilentTargetDialog = false },
            title = { Text("Mute Target Preference", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "SILENT" to "Total Silence (DND)",
                        "VIBRATE" to "Vibrate only"
                    ).forEach { (target, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setSmartSilentTarget(target)
                                    showSilentTargetDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (smartSilentTarget == target),
                                onClick = {
                                    viewModel.setSmartSilentTarget(target)
                                    showSilentTargetDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSilentTargetDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showPermissionsDialog) {
        PermissionsReadinessDialog(
            onDismiss = { showPermissionsDialog = false },
            onPermissionChanged = { viewModel.triggerAllUpdates() }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun SettingsClickableItem(
    painter: Painter,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "›",
            fontSize = 20.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun SettingsSwitchItem(
    painter: Painter,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
