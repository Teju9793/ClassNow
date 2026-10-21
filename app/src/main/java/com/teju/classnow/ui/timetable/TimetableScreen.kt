package com.teju.classnow.ui.timetable

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teju.classnow.R
import com.teju.classnow.data.model.CustomClassSchedule
import com.teju.classnow.data.model.CustomSoundInterval
import com.teju.classnow.data.model.TimetableEntry
import com.teju.classnow.domain.timetable.TimetableStatus
import com.teju.classnow.ui.viewmodel.TimetableViewModel
import com.teju.classnow.util.CsvHelper
import com.teju.classnow.util.CustomScheduleHelper
import com.teju.classnow.util.TimeInputHelper
import com.teju.classnow.util.TimeUtils
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(viewModel: TimetableViewModel, onBack: () -> Unit) {
    val selectedDay by viewModel.selectedDay.collectAsState()
    val entries by viewModel.currentDayEntries.collectAsState()
    val allEntriesList by viewModel.allEntries.collectAsState()
    val importPreview by viewModel.importPreview.collectAsState()
    val scheduleState by viewModel.scheduleState.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()
    val currentDayName = remember(currentTime) { viewModel.getCurrentDayName() }

    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<TimetableEntry?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var entryToDelete by remember { mutableStateOf<TimetableEntry?>(null) }
    var showClearAllConfirmation by remember { mutableStateOf(false) }

    val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.prepareImport(it, context) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let { saveCsvToUri(context, it, viewModel.getCsvExportData()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Timetable", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Import CSV") },
                            onClick = {
                                showMenu = false
                                importLauncher.launch(arrayOf("text/comma-separated-values", "text/csv"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export CSV") },
                            onClick = {
                                showMenu = false
                                exportLauncher.launch("timetable_export.csv")
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Clear All Data") },
                            onClick = {
                                showMenu = false
                                showClearAllConfirmation = true
                            },
                            colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add New Class")
            }
        },
        modifier = Modifier.imePadding()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = days.indexOf(selectedDay).coerceAtLeast(0),
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                days.forEach { day ->
                    Tab(
                        selected = selectedDay == day,
                        onClick = { viewModel.selectDay(day) },
                        text = {
                            Text(
                                text = day,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (selectedDay == day) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No classes scheduled for $selectedDay.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { showAddDialog = true },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("+ Add Class")
                            }
                            OutlinedButton(
                                onClick = {
                                    importLauncher.launch(arrayOf("text/comma-separated-values", "text/csv"))
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Import CSV")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        val isToday = selectedDay.equals(currentDayName, ignoreCase = true)
                        val isEntryActive = isToday &&
                                scheduleState.status == TimetableStatus.CURRENT_CLASS &&
                                scheduleState.currentClass?.id == entry.id

                        TimetableEntryCard(
                            entry = entry,
                            isActive = isEntryActive,
                            onEdit = { editingEntry = entry },
                            onDelete = { entryToDelete = entry },
                            onDuplicate = { viewModel.duplicateEntry(entry) },
                            onMoveUp = { viewModel.moveEntryUp(entry) },
                            onMoveDown = { viewModel.moveEntryDown(entry) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditClassDialog(
            selectedDay = selectedDay,
            existingEntry = null,
            allEntries = allEntriesList,
            onDismiss = { showAddDialog = false },
            onSave = { subject, start, end, faculty, day, silentRule, customSchedule ->
                viewModel.addEntry(subject, start, end, faculty, day, silentRule, customSchedule)
            }
        )
    }

    editingEntry?.let { entry ->
        AddEditClassDialog(
            selectedDay = entry.dayOfWeek,
            existingEntry = entry,
            allEntries = allEntriesList,
            onDismiss = { editingEntry = null },
            onSave = { subject, start, end, faculty, day, silentRule, customSchedule ->
                viewModel.updateEntry(
                    entry.copy(
                        subject = subject,
                        startTime = start,
                        endTime = end,
                        faculty = faculty,
                        dayOfWeek = day,
                        silentModeRule = silentRule,
                        customSilentSchedule = customSchedule
                    )
                )
            }
        )
    }

    entryToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete Class") },
            text = { Text("Are you sure you want to delete '${entry.subject}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteEntry(entry)
                        entryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showClearAllConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirmation = false },
            title = { Text("Clear All Data") },
            text = { Text("Are you sure you want to delete your entire timetable? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllEntries()
                        showClearAllConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear All") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirmation = false }) { Text("Cancel") }
            }
        )
    }

    importPreview?.let { preview ->
        ImportPreviewDialog(
            preview = preview,
            onDismiss = { viewModel.clearImportPreview() },
            onConfirm = { replace -> viewModel.confirmImport(replace) }
        )
    }
}

@Composable
fun TimetableEntryCard(
    entry: TimetableEntry,
    isActive: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    var cardMenuExpanded by remember { mutableStateOf(false) }

    val animatedCardBg by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.70f)
                      else MaterialTheme.colorScheme.surface,
        animationSpec = tween(350),
        label = "cardBg"
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        animationSpec = tween(350),
        label = "borderColor"
    )
    val animatedBorderWidth by animateDpAsState(
        targetValue = if (isActive) 2.dp else 1.dp,
        animationSpec = tween(350),
        label = "borderWidth"
    )
    val animatedElevation by animateDpAsState(
        targetValue = if (isActive) 4.dp else 1.dp,
        animationSpec = tween(350),
        label = "elevation"
    )
    val animatedAccentWidth by animateDpAsState(
        targetValue = if (isActive) 6.dp else 4.dp,
        animationSpec = tween(350),
        label = "accentWidth"
    )
    val animatedAccentHeight by animateDpAsState(
        targetValue = if (isActive) 60.dp else 52.dp,
        animationSpec = tween(350),
        label = "accentHeight"
    )
    val animatedAccentColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        animationSpec = tween(350),
        label = "accentColor"
    )
    val animatedTextColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(350),
        label = "textColor"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = animatedCardBg,
        tonalElevation = animatedElevation,
        border = BorderStroke(
            width = animatedBorderWidth,
            color = animatedBorderColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left vertical accent indicator
            Box(
                modifier = Modifier
                    .width(animatedAccentWidth)
                    .height(animatedAccentHeight)
                    .clip(RoundedCornerShape(3.dp))
                    .background(animatedAccentColor)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                AnimatedVisibility(
                    visible = isActive,
                    enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                    exit = shrinkVertically(tween(250)) + fadeOut(tween(250))
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onPrimary)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "HAPPENING NOW",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                Text(
                    text = entry.subject,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = animatedTextColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${TimeUtils.formatTo12Hour(entry.startTime)} – ${TimeUtils.formatTo12Hour(entry.endTime)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )

                if (!entry.faculty.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Faculty: ${entry.faculty}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (entry.silentModeRule != TimetableEntry.RULE_DEFAULT) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (entry.silentModeRule) {
                            TimetableEntry.RULE_FORCE_SILENT -> MaterialTheme.colorScheme.secondaryContainer
                            TimetableEntry.RULE_FORCE_SOUND -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = when (entry.silentModeRule) {
                                TimetableEntry.RULE_FORCE_SILENT -> "Always Silent"
                                TimetableEntry.RULE_FORCE_SOUND -> "Always Sound"
                                TimetableEntry.RULE_IGNORE -> "Ignore Sound"
                                else -> ""
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = when (entry.silentModeRule) {
                                TimetableEntry.RULE_FORCE_SILENT -> MaterialTheme.colorScheme.onSecondaryContainer
                                TimetableEntry.RULE_FORCE_SOUND -> MaterialTheme.colorScheme.onTertiaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                if (!entry.customSilentSchedule.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Custom Sound Schedule",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box {
                IconButton(
                    onClick = { cardMenuExpanded = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Class options for ${entry.subject}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = cardMenuExpanded, onDismissRequest = { cardMenuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { cardMenuExpanded = false; onEdit() })
                    DropdownMenuItem(text = { Text("Move Up") }, onClick = { cardMenuExpanded = false; onMoveUp() })
                    DropdownMenuItem(text = { Text("Move Down") }, onClick = { cardMenuExpanded = false; onMoveDown() })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { cardMenuExpanded = false; onDuplicate() })
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { cardMenuExpanded = false; onDelete() },
                        colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error)
                    )
                }
            }
        }
    }
}

@Composable
fun TimeInputField(
    title: String,
    hour: String,
    onHourChange: (String) -> Unit,
    minute: String,
    onMinuteChange: (String) -> Unit,
    isPm: Boolean,
    onPmChange: (Boolean) -> Unit,
    onOpenClockPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isNarrow = maxWidth < 310.dp

            if (isNarrow) {
                // Responsive 2-row layout for narrow widths, high font scaling, and custom interval cards
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Hour field (1-12)
                        OutlinedTextField(
                            value = hour,
                            onValueChange = { newVal ->
                                val filtered = newVal.filter { it.isDigit() }.take(2)
                                onHourChange(filtered)
                            },
                            placeholder = { Text("09", color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), textAlign = TextAlign.Center) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
                            modifier = Modifier
                                .width(58.dp)
                                .onFocusChanged { focusState ->
                                    if (!focusState.isFocused && hour.isNotBlank()) {
                                        TimeInputHelper.normalizeHour(hour, isPm)?.let { (normalizedHour, autoPm) ->
                                            onHourChange(normalizedHour)
                                            if (autoPm != isPm) {
                                                onPmChange(autoPm)
                                            }
                                        }
                                    }
                                }
                        )

                        Text(" : ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                        // Minute field (00-59)
                        OutlinedTextField(
                            value = minute,
                            onValueChange = { newVal ->
                                val filtered = newVal.filter { it.isDigit() }.take(2)
                                onMinuteChange(filtered)
                            },
                            placeholder = { Text("00", color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), textAlign = TextAlign.Center) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
                            modifier = Modifier
                                .width(58.dp)
                                .onFocusChanged { focusState ->
                                    if (!focusState.isFocused && minute.isNotBlank()) {
                                        TimeInputHelper.normalizeMinute(minute)?.let { normalizedMinute ->
                                            onMinuteChange(normalizedMinute)
                                        }
                                    }
                                }
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        // Prominent, dedicated clock picker button
                        FilledTonalIconButton(
                            onClick = onOpenClockPicker,
                            modifier = Modifier.size(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_schedule),
                                contentDescription = "Pick $title on clock",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // AM / PM row with full visibility and comfortable touch targets
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = !isPm,
                            onClick = { onPmChange(false) },
                            label = { Text("AM", fontWeight = if (!isPm) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.height(40.dp)
                        )
                        FilterChip(
                            selected = isPm,
                            onClick = { onPmChange(true) },
                            label = { Text("PM", fontWeight = if (isPm) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.height(40.dp)
                        )
                    }
                }
            } else {
                // Single-row layout when width permits (>= 310.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Hour field (1-12)
                    OutlinedTextField(
                        value = hour,
                        onValueChange = { newVal ->
                            val filtered = newVal.filter { it.isDigit() }.take(2)
                            onHourChange(filtered)
                        },
                        placeholder = { Text("09", color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), textAlign = TextAlign.Center) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
                        modifier = Modifier
                            .width(58.dp)
                            .onFocusChanged { focusState ->
                                if (!focusState.isFocused && hour.isNotBlank()) {
                                    TimeInputHelper.normalizeHour(hour, isPm)?.let { (normalizedHour, autoPm) ->
                                        onHourChange(normalizedHour)
                                        if (autoPm != isPm) {
                                            onPmChange(autoPm)
                                        }
                                    }
                                }
                            }
                    )

                    Text(":", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                    // Minute field (00-59)
                    OutlinedTextField(
                        value = minute,
                        onValueChange = { newVal ->
                            val filtered = newVal.filter { it.isDigit() }.take(2)
                            onMinuteChange(filtered)
                        },
                        placeholder = { Text("00", color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), textAlign = TextAlign.Center) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
                        modifier = Modifier
                            .width(58.dp)
                            .onFocusChanged { focusState ->
                                if (!focusState.isFocused && minute.isNotBlank()) {
                                    TimeInputHelper.normalizeMinute(minute)?.let { normalizedMinute ->
                                        onMinuteChange(normalizedMinute)
                                    }
                                }
                            }
                    )

                    // High-contrast AM / PM Selector Chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = !isPm,
                            onClick = { onPmChange(false) },
                            label = { Text("AM", fontWeight = if (!isPm) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.height(40.dp)
                        )
                        FilterChip(
                            selected = isPm,
                            onClick = { onPmChange(true) },
                            label = { Text("PM", fontWeight = if (isPm) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.height(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Prominent, high-contrast clock picker button
                    FilledTonalIconButton(
                        onClick = onOpenClockPicker,
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_schedule),
                            contentDescription = "Pick $title on clock",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

data class EditableInterval(
    val id: Long = System.currentTimeMillis() + (0..9999).random(),
    var startHour: String = "09",
    var startMinute: String = "00",
    var startIsPm: Boolean = false,
    var endHour: String = "09",
    var endMinute: String = "30",
    var endIsPm: Boolean = false,
    var mode: String = CustomSoundInterval.MODE_SILENT
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditClassDialog(
    selectedDay: String,
    existingEntry: TimetableEntry?,
    allEntries: List<TimetableEntry>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String?, String, String, String?) -> Unit
) {
    var subject by remember { mutableStateOf(existingEntry?.subject ?: "") }
    var faculty by remember { mutableStateOf(existingEntry?.faculty ?: "") }
    var dayChoice by remember { mutableStateOf(existingEntry?.dayOfWeek ?: selectedDay) }
    var silentRule by remember { mutableStateOf(existingEntry?.silentModeRule ?: TimetableEntry.RULE_DEFAULT) }
    var errorMessage by remember { mutableStateOf("") }

    // Start time 12-hour fields
    val initialStart = remember(existingEntry) {
        if (existingEntry != null) TimeInputHelper.from24Hour(existingEntry.startTime)
        else TimeInputHelper.Time12("09", "00", false)
    }
    var startHour by remember { mutableStateOf(initialStart.hour) }
    var startMinute by remember { mutableStateOf(initialStart.minute) }
    var startIsPm by remember { mutableStateOf(initialStart.isPm) }

    // End time 12-hour fields
    val initialEnd = remember(existingEntry) {
        if (existingEntry != null) TimeInputHelper.from24Hour(existingEntry.endTime)
        else TimeInputHelper.Time12("10", "00", false)
    }
    var endHour by remember { mutableStateOf(initialEnd.hour) }
    var endMinute by remember { mutableStateOf(initialEnd.minute) }
    var endIsPm by remember { mutableStateOf(initialEnd.isPm) }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var activeIntervalPicker by remember { mutableStateOf<Triple<Int, Boolean, Pair<Int, Int>>?>(null) }
    var dayDropdownExpanded by remember { mutableStateOf(false) }

    val daysList = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    // Custom Schedule state
    val parsedSchedule = remember(existingEntry) {
        existingEntry?.customSilentSchedule?.let { CustomScheduleHelper.fromJson(it) }
    }
    var enableCustomSchedule by remember { mutableStateOf(parsedSchedule != null && parsedSchedule.intervals.isNotEmpty()) }
    var endActionChoice by remember { mutableStateOf(parsedSchedule?.endAction ?: CustomClassSchedule.END_ACTION_DEFAULT) }

    val customIntervals = remember(existingEntry) {
        mutableStateListOf<EditableInterval>().apply {
            if (parsedSchedule != null && parsedSchedule.intervals.isNotEmpty()) {
                parsedSchedule.intervals.forEach { interval ->
                    val s12 = TimeInputHelper.from24Hour(interval.startTime)
                    val e12 = TimeInputHelper.from24Hour(interval.endTime)
                    add(
                        EditableInterval(
                            startHour = s12.hour,
                            startMinute = s12.minute,
                            startIsPm = s12.isPm,
                            endHour = e12.hour,
                            endMinute = e12.minute,
                            endIsPm = e12.isPm,
                            mode = interval.mode
                        )
                    )
                }
            }
        }
    }

    val scrollState = rememberScrollState()

    // Smoothly bring the custom schedule editor into view when enabled
    LaunchedEffect(enableCustomSchedule) {
        if (enableCustomSchedule) {
            kotlinx.coroutines.delay(150)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingEntry == null) "Add Class" else "Edit Class", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Day Selection Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = dayChoice,
                        onValueChange = {},
                        label = { Text("Day") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dayDropdownExpanded = true },
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { dayDropdownExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Choose Day")
                            }
                        }
                    )
                    DropdownMenu(expanded = dayDropdownExpanded, onDismissRequest = { dayDropdownExpanded = false }) {
                        daysList.forEach { d ->
                            DropdownMenuItem(text = { Text(d) }, onClick = { dayChoice = d; dayDropdownExpanded = false })
                        }
                    }
                }

                // Start Time Field (Editable 12-hr with AM/PM & clock picker)
                TimeInputField(
                    title = "Start Time",
                    hour = startHour,
                    onHourChange = { startHour = it },
                    minute = startMinute,
                    onMinuteChange = { startMinute = it },
                    isPm = startIsPm,
                    onPmChange = { startIsPm = it },
                    onOpenClockPicker = { showStartPicker = true }
                )

                // End Time Field (Editable 12-hr with AM/PM & clock picker)
                TimeInputField(
                    title = "End Time",
                    hour = endHour,
                    onHourChange = { endHour = it },
                    minute = endMinute,
                    onMinuteChange = { endMinute = it },
                    isPm = endIsPm,
                    onPmChange = { endIsPm = it },
                    onOpenClockPicker = { showEndPicker = true }
                )

                OutlinedTextField(
                    value = faculty,
                    onValueChange = { faculty = it },
                    label = { Text("Faculty (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Silent Mode Rule
                Column {
                    Text("Smart Silent Rule", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    listOf(
                        TimetableEntry.RULE_DEFAULT to "Default Automation",
                        TimetableEntry.RULE_FORCE_SILENT to "Force Silent ON",
                        TimetableEntry.RULE_FORCE_SOUND to "Force Sound ON",
                        TimetableEntry.RULE_IGNORE to "Ignore / Keep As Is"
                    ).forEach { (rule, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { silentRule = rule }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = silentRule == rule,
                                onClick = { silentRule = rule }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // Custom Sound Schedule Toggle & Builder
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { enableCustomSchedule = !enableCustomSchedule }
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Custom Sound Schedule",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Configure custom silent/sound periods inside this class",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enableCustomSchedule,
                        onCheckedChange = { enableCustomSchedule = it }
                    )
                }

                AnimatedVisibility(
                    visible = enableCustomSchedule,
                    enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(250)),
                    exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200))
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Sound Intervals",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            if (customIntervals.isNotEmpty()) {
                                Text(
                                    text = "${customIntervals.size} added",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (customIntervals.isEmpty()) {
                            Text(
                                text = "No custom intervals added yet. Tap 'Add Sound Interval' below to configure specific silent or normal sound periods within this class.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        customIntervals.forEachIndexed { index, interval ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Interval #${index + 1}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { customIntervals.removeAt(index) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete interval",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    TimeInputField(
                                        title = "From",
                                        hour = interval.startHour,
                                        onHourChange = { customIntervals[index] = interval.copy(startHour = it) },
                                        minute = interval.startMinute,
                                        onMinuteChange = { customIntervals[index] = interval.copy(startMinute = it) },
                                        isPm = interval.startIsPm,
                                        onPmChange = { customIntervals[index] = interval.copy(startIsPm = it) },
                                        onOpenClockPicker = {
                                            val h24 = TimeInputHelper.to24Hour(interval.startHour, interval.startMinute, interval.startIsPm)
                                            val parsed = try {
                                                h24?.let { LocalTime.parse(it) } ?: LocalTime.of(9, 0)
                                            } catch (e: Exception) {
                                                LocalTime.of(9, 0)
                                            }
                                            activeIntervalPicker = Triple(index, true, parsed.hour to parsed.minute)
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    TimeInputField(
                                        title = "To",
                                        hour = interval.endHour,
                                        onHourChange = { customIntervals[index] = interval.copy(endHour = it) },
                                        minute = interval.endMinute,
                                        onMinuteChange = { customIntervals[index] = interval.copy(endMinute = it) },
                                        isPm = interval.endIsPm,
                                        onPmChange = { customIntervals[index] = interval.copy(endIsPm = it) },
                                        onOpenClockPicker = {
                                            val h24 = TimeInputHelper.to24Hour(interval.endHour, interval.endMinute, interval.endIsPm)
                                            val parsed = try {
                                                h24?.let { LocalTime.parse(it) } ?: LocalTime.of(10, 0)
                                            } catch (e: Exception) {
                                                LocalTime.of(10, 0)
                                            }
                                            activeIntervalPicker = Triple(index, false, parsed.hour to parsed.minute)
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        FilterChip(
                                            selected = interval.mode == CustomSoundInterval.MODE_SILENT,
                                            onClick = { customIntervals[index] = interval.copy(mode = CustomSoundInterval.MODE_SILENT) },
                                            label = { Text("Silent Mode") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                                            )
                                        )
                                        FilterChip(
                                            selected = interval.mode == CustomSoundInterval.MODE_NORMAL,
                                            onClick = { customIntervals[index] = interval.copy(mode = CustomSoundInterval.MODE_NORMAL) },
                                            label = { Text("Normal Sound") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onTertiary
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                customIntervals.add(
                                    EditableInterval(
                                        startHour = startHour,
                                        startMinute = startMinute,
                                        startIsPm = startIsPm,
                                        endHour = endHour,
                                        endMinute = endMinute,
                                        endIsPm = endIsPm,
                                        mode = CustomSoundInterval.MODE_SILENT
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Sound Interval")
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "When class ends:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        val endActions = listOf(
                            CustomClassSchedule.END_ACTION_DEFAULT to "Default (Follow global rules)",
                            CustomClassSchedule.END_ACTION_NORMAL to "Force Normal Sound",
                            CustomClassSchedule.END_ACTION_KEEP to "Keep Current Sound State"
                        )
                        endActions.forEach { (action, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { endActionChoice = action }
                                    .padding(vertical = 2.dp)
                            ) {
                                RadioButton(
                                    selected = endActionChoice == action,
                                    onClick = { endActionChoice = action }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(label, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (subject.trim().isBlank()) {
                        errorMessage = "Please enter a subject name."
                        return@Button
                    }
                    val start24 = TimeInputHelper.to24Hour(startHour, startMinute, startIsPm)
                    if (start24 == null) {
                        errorMessage = "Please enter a valid Start Time (Hour 1-12, Minute 00-59)."
                        return@Button
                    }
                    val end24 = TimeInputHelper.to24Hour(endHour, endMinute, endIsPm)
                    if (end24 == null) {
                        errorMessage = "Please enter a valid End Time (Hour 1-12, Minute 00-59)."
                        return@Button
                    }

                    val startL = try { LocalTime.parse(start24) } catch (e: Exception) { null }
                    val endL = try { LocalTime.parse(end24) } catch (e: Exception) { null }
                    if (startL == null || endL == null) {
                        errorMessage = "Invalid time format."
                        return@Button
                    }
                    if (!endL.isAfter(startL)) {
                        errorMessage = "End time must be after start time."
                        return@Button
                    }

                    // Overlap/Conflict Check
                    val overlap = allEntries.filter { it.dayOfWeek == dayChoice }.find { existing ->
                        if (existingEntry != null && existing.id == existingEntry.id) return@find false
                        val exStart = LocalTime.parse(existing.startTime)
                        val exEnd = LocalTime.parse(existing.endTime)
                        startL.isBefore(exEnd) && endL.isAfter(exStart)
                    }

                    if (overlap != null) {
                        errorMessage = "Time conflicts with '${overlap.subject}' on $dayChoice."
                        return@Button
                    }

                    var customScheduleJson: String? = null
                    if (enableCustomSchedule && customIntervals.isNotEmpty()) {
                        val intervalsList = mutableListOf<CustomSoundInterval>()
                        for (i in customIntervals.indices) {
                            val it = customIntervals[i]
                            val s24 = TimeInputHelper.to24Hour(it.startHour, it.startMinute, it.startIsPm)
                            val e24 = TimeInputHelper.to24Hour(it.endHour, it.endMinute, it.endIsPm)
                            if (s24 == null || e24 == null) {
                                errorMessage = "Please enter valid times for Interval #${i + 1}."
                                return@Button
                            }
                            intervalsList.add(CustomSoundInterval(startTime = s24, endTime = e24, mode = it.mode))
                        }
                        val validationError = CustomScheduleHelper.validate(intervalsList, start24, end24)
                        if (validationError != null) {
                            errorMessage = validationError
                            return@Button
                        }
                        val schedule = CustomClassSchedule(
                            intervals = intervalsList,
                            endAction = endActionChoice
                        )
                        customScheduleJson = CustomScheduleHelper.toJson(schedule)
                    }

                    onSave(subject.trim(), start24, end24, faculty.takeIf { it.isNotBlank() }, dayChoice, silentRule, customScheduleJson)
                    onDismiss()
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showStartPicker) {
        val currentStart24 = TimeInputHelper.to24Hour(startHour, startMinute, startIsPm)
        val parsedStart = try {
            currentStart24?.let { LocalTime.parse(it) } ?: LocalTime.of(9, 0)
        } catch (e: Exception) {
            LocalTime.of(9, 0)
        }
        M3TimePickerDialog(
            initialHour = parsedStart.hour,
            initialMinute = parsedStart.minute,
            onDismiss = { showStartPicker = false },
            onTimeSelected = { h, m ->
                val time12 = TimeInputHelper.from24Hour(String.format(Locale.US, "%02d:%02d", h, m))
                startHour = time12.hour
                startMinute = time12.minute
                startIsPm = time12.isPm
            }
        )
    }

    if (showEndPicker) {
        val currentEnd24 = TimeInputHelper.to24Hour(endHour, endMinute, endIsPm)
        val parsedEnd = try {
            currentEnd24?.let { LocalTime.parse(it) } ?: LocalTime.of(10, 0)
        } catch (e: Exception) {
            LocalTime.of(10, 0)
        }
        M3TimePickerDialog(
            initialHour = parsedEnd.hour,
            initialMinute = parsedEnd.minute,
            onDismiss = { showEndPicker = false },
            onTimeSelected = { h, m ->
                val time12 = TimeInputHelper.from24Hour(String.format(Locale.US, "%02d:%02d", h, m))
                endHour = time12.hour
                endMinute = time12.minute
                endIsPm = time12.isPm
            }
        )
    }

    activeIntervalPicker?.let { (idx, isStart, initialTime) ->
        M3TimePickerDialog(
            initialHour = initialTime.first,
            initialMinute = initialTime.second,
            onDismiss = { activeIntervalPicker = null },
            onTimeSelected = { h, m ->
                val time12 = TimeInputHelper.from24Hour(String.format(Locale.US, "%02d:%02d", h, m))
                if (idx in customIntervals.indices) {
                    val currentInt = customIntervals[idx]
                    customIntervals[idx] = if (isStart) {
                        currentInt.copy(startHour = time12.hour, startMinute = time12.minute, startIsPm = time12.isPm)
                    } else {
                        currentInt.copy(endHour = time12.hour, endMinute = time12.minute, endIsPm = time12.isPm)
                    }
                }
                activeIntervalPicker = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun M3TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onTimeSelected: (Int, Int) -> Unit
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                onTimeSelected(state.hour, state.minute)
                onDismiss()
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text("Select Time") },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        }
    )
}

@Composable
fun ImportPreviewDialog(
    preview: CsvHelper.ParseResult,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit
) {
    var replaceExisting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Preview", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (preview.errors.isNotEmpty()) {
                    Text("Errors found:", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.heightIn(max = 100.dp)) {
                        LazyColumn {
                            items(preview.errors) { error ->
                                Text("• $error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (preview.warnings.isNotEmpty()) {
                    Text("Warnings:", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.heightIn(max = 100.dp)) {
                        LazyColumn {
                            items(preview.warnings) { warning ->
                                Text("• $warning", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (preview.entries.isNotEmpty()) {
                    Text("Valid Entries to Import: ${preview.entries.size}", fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.heightIn(max = 200.dp)) {
                        LazyColumn {
                            items(preview.entries) { entry ->
                                Text("${entry.dayOfWeek}: ${entry.subject} (${TimeUtils.formatTo12Hour(entry.startTime)}-${TimeUtils.formatTo12Hour(entry.endTime)})", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(checked = replaceExisting, onCheckedChange = { replaceExisting = it })
                        Text("Replace existing timetable", style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (preview.errors.isEmpty()) {
                    Text("No valid entries found in CSV.")
                }
            }
        },
        confirmButton = {
            if (preview.entries.isNotEmpty() && preview.errors.isEmpty()) {
                Button(onClick = { onConfirm(replaceExisting) }) {
                    Text("Import")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (preview.errors.isNotEmpty()) "Close" else "Cancel")
            }
        }
    )
}

private fun saveCsvToUri(context: Context, uri: Uri, data: String) {
    try {
        context.contentResolver.openOutputStream(uri)?.use {
            it.write(data.toByteArray())
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
