package com.teju.classnow.ui.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.teju.classnow.util.PermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsReadinessDialog(
    onDismiss: () -> Unit,
    onPermissionChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasDnd by remember { mutableStateOf(PermissionHelper.hasDndPermission(context)) }
    var hasNotifications by remember { mutableStateOf(PermissionHelper.hasNotificationPermission(context)) }
    var hasExactAlarm by remember { mutableStateOf(PermissionHelper.hasExactAlarmPermission(context)) }

    fun refreshPermissions() {
        hasDnd = PermissionHelper.hasDndPermission(context)
        hasNotifications = PermissionHelper.hasNotificationPermission(context)
        hasExactAlarm = PermissionHelper.hasExactAlarmPermission(context)
        onPermissionChanged()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        refreshPermissions()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Feature Readiness",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Permissions for sound automation & class schedule alerts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Do Not Disturb Access
                PermissionItem(
                    icon = Icons.Default.Notifications,
                    title = "Sound Mode & DND Access",
                    subtitle = "Required for Smart Silent Mode to mute or vibrate your phone during classes and restore sound afterward.",
                    isGranted = hasDnd,
                    statusText = if (hasDnd) "Granted" else "Action Required",
                    actionLabel = "Grant Access",
                    onAction = {
                        PermissionHelper.openDndSettings(context)
                    }
                )

                // 2. Exact Alarms
                PermissionItem(
                    icon = Icons.Default.Refresh,
                    title = "Exact Transition Alarms",
                    subtitle = "Ensures class boundaries and intermediate widget progress checkpoints trigger precisely on schedule.",
                    isGranted = hasExactAlarm,
                    statusText = if (hasExactAlarm) "Exact Timing Active" else "Inexact Alarms Only",
                    actionLabel = "Allow Exact",
                    onAction = {
                        PermissionHelper.openExactAlarmSettings(context)
                    }
                )

                // 3. Notifications
                PermissionItem(
                    icon = Icons.Default.Info,
                    title = "Timetable Notifications",
                    subtitle = "Shows live ongoing class progress in your notification shade and sends timetable alerts.",
                    isGranted = hasNotifications,
                    statusText = if (hasNotifications) "Allowed" else "Optional",
                    actionLabel = "Allow",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            PermissionHelper.openAppSettings(context)
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    statusText: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
            }
        ),
        border = BorderStroke(
            1.dp,
            if (isGranted) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isGranted) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.errorContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isGranted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = if (isGranted) Color(0xFF00A86B) else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isGranted) Color(0xFF00A86B) else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (!isGranted) {
                    Spacer(modifier = Modifier.width(6.dp))
                    FilledTonalButton(
                        onClick = onAction,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text(actionLabel, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}
