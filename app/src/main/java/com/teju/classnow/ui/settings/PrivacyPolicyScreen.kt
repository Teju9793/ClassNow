package com.teju.classnow.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "100% Offline & Private",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Effective Date: October 2026 • Version 1.0.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Section 1: Data Storage & Transmission
            PolicySection(
                title = "1. Data Storage & Zero Network Transmission",
                content = "ClassNow is architected as an offline-first utility. All timetable entries, subject names, faculty information, custom sound intervals, and preferences are stored exclusively on your device inside a local Room SQLite database and encrypted Android DataStore.\n\n" +
                        "The application does not request the INTERNET permission in its manifest and does not maintain any remote servers, cloud databases, or third-party tracking services. Your academic schedule never leaves your device."
            )

            // Section 2: Permissions Used and Purpose
            PolicySection(
                title = "2. Device Permissions & Why They Are Needed",
                content = "ClassNow requests only the minimal Android system capabilities required to automate your timetable and phone volume:\n\n" +
                        "• Do Not Disturb Access (ACCESS_NOTIFICATION_POLICY):\n" +
                        "Enables Smart Silent Mode to switch between Silent, Vibrate, and Normal sound modes when your scheduled classes start and end.\n\n" +
                        "• Exact Alarms (SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM):\n" +
                        "Required by Android to wake the scheduler precisely at class start and end boundaries, ensuring your phone mutes immediately without delay.\n\n" +
                        "• Notifications (POST_NOTIFICATIONS):\n" +
                        "Optional. Used solely to render ongoing schedule status on your lock screen and notification drawer.\n\n" +
                        "• Boot Completed (RECEIVE_BOOT_COMPLETED):\n" +
                        "Allows the application to restore scheduled alarms and refresh home-screen widgets when your device is restarted.\n\n" +
                        "• Vibration (VIBRATE):\n" +
                        "Provides haptic feedback and activates vibrate ringer modes."
            )

            // Section 3: Third-Party SDKs & Telemetry
            PolicySection(
                title = "3. Third-Party Services & Telemetry",
                content = "ClassNow contains zero advertising SDKs, zero user tracking libraries, and zero external telemetry services. We do not sell, rent, monetize, or transmit any user data."
            )

            // Section 4: Data Retention & Deletion
            PolicySection(
                title = "4. User Control, Backup & Data Deletion",
                content = "You maintain complete ownership of your data at all times:\n\n" +
                        "• CSV Export: You can export your timetable into an open, standard CSV file anytime.\n" +
                        "• Total Deletion: Deleting a class or clearing app storage in Android System Settings permanently purges all records from your local storage."
            )

            // Section 5: Policy Updates & Contact
            PolicySection(
                title = "5. Contact & Questions",
                content = "If you have questions regarding this Privacy Policy or wish to report a security inquiry, contact the developer directly:\n\n" +
                        "Developer: K. R. Tejas Kumar\n" +
                        "Email: tteju97933@gmail.com\n\n" +
                        "Note: This in-app policy reflects actual codebase behavior. When required for app marketplace distribution, an identical copy is hosted online."
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PolicySection(
    title: String,
    content: String
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            )
        }
    }
}
