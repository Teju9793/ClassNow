package com.teju.classnow.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpDeskScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help & Support", fontWeight = FontWeight.Bold) },
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
            // Support Contact Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Need Assistance?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Have a question, feature request, or found a bug? Send an email directly to developer support.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { launchSupportEmail(context) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Email Support (tteju97933@gmail.com)", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                text = "FREQUENTLY ASKED QUESTIONS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            // FAQ 1: Adding and Editing Timetable
            FaqItem(
                question = "How do I add or edit timetable classes?",
                answer = "Open 'Manage Timetable' from the Home screen or Settings. Select the day tab (Monday through Sunday) and tap the '+' button. Enter the Subject, Start Time, End Time, and optional Faculty or Custom Intervals. You can edit or delete an existing class anytime by tapping its menu icon on the class card."
            )

            // FAQ 2: Widget Not Refreshing / Stale Data
            FaqItem(
                question = "Why does my widget look stale or delayed?",
                answer = "Android optimizes battery by batching widget redraws. You can tap the Refresh button (circular arrow icon) on the widget at any moment to trigger an immediate update.\n\nAlso, ensure that your device has not restricted background execution for ClassNow (see battery settings below)."
            )

            // FAQ 3: Understanding Current and Next Classes
            FaqItem(
                question = "How does ClassNow detect Current and Next class?",
                answer = "ClassNow uses a unified schedule engine that evaluates your current local time against today's entries:\n\n• If time falls within a scheduled class: Card 1 shows the Active Class and Card 2 shows what comes Next.\n• During lunch, breaks, or free periods: Card 1 shows the break duration and Card 2 displays the next actual subject.\n• After the final class: the widget displays 'Done for Today' with no empty containers."
            )

            // FAQ 4: CSV Import and Export
            FaqItem(
                question = "How does CSV Import & Export work?",
                answer = "You can share or back up your entire weekly timetable via CSV:\n\n• Export: In Manage Timetable, tap Export CSV to generate a standard comma-separated file.\n• Import: Tap Import CSV to load classes. ClassNow automatically validates headers, 12-hour/24-hour time formats, and displays a preview before saving to prevent invalid entries."
            )

            // FAQ 5: Smart Silent Mode
            FaqItem(
                question = "What permissions does Smart Silent Mode need?",
                answer = "Smart Silent Mode requires Do Not Disturb (Notification Policy) access and Exact Alarm permission. With these permissions granted, ClassNow sets exact system boundary alarms to mute your phone when classes begin and restore normal sound when classes end."
            )

            // FAQ 6: Manufacturer Battery Restrictions (MIUI / HyperOS / Samsung)
            FaqItem(
                question = "Why do Xiaomi, MIUI, or Samsung devices delay sound mode?",
                answer = "Aggressive manufacturer battery optimizations (such as Xiaomi MIUI/HyperOS, Samsung OneUI, or OnePlus OxygenOS) can freeze background alarm broadcasts.\n\nTo ensure 100% reliable sound automation:\n1. Open your device Settings -> Apps -> ClassNow.\n2. Enable 'Autostart' or 'Background Activity'.\n3. Set Battery Saver to 'No restrictions'."
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FaqItem(
    question: String,
    answer: String
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .clickable { isExpanded = !isExpanded }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = question,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (isExpanded) "▲" else "▼",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = answer,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

private fun launchSupportEmail(context: Context) {
    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:tteju97933@gmail.com")
        putExtra(Intent.EXTRA_SUBJECT, "ClassNow Support")
    }
    try {
        context.startActivity(Intent.createChooser(emailIntent, "Send Email via..."))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No email app found. Please email tteju97933@gmail.com directly.", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to launch email client.", Toast.LENGTH_SHORT).show()
    }
}
