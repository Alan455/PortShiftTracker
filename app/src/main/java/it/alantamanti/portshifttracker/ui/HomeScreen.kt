package it.alantamanti.portshifttracker.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity
import it.alantamanti.portshifttracker.data.local.AppFeatureStore
import it.alantamanti.portshifttracker.data.local.ShiftEntity
import it.alantamanti.portshifttracker.data.local.SpecialDayOverride
import it.alantamanti.portshifttracker.data.local.WorkerEntity
import it.alantamanti.portshifttracker.data.repository.DuplicatePerformanceException
import it.alantamanti.portshifttracker.data.repository.PortRepository
import it.alantamanti.portshifttracker.data.repository.normalizeSelectedRuleIds
import it.alantamanti.portshifttracker.data.repository.selectionValidationMessage
import it.alantamanti.portshifttracker.data.repository.ShiftWithPay
import it.alantamanti.portshifttracker.data.repository.toDomain
import it.alantamanti.portshifttracker.domain.AllowanceApplicationMode
import it.alantamanti.portshifttracker.domain.AllowanceCalculationType
import it.alantamanti.portshifttracker.domain.AllowanceCalculator
import it.alantamanti.portshifttracker.domain.AllowanceCategory
import it.alantamanti.portshifttracker.domain.BasePayMode
import it.alantamanti.portshifttracker.domain.PerformanceType
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

@Composable
internal fun HomeScreen(repository: PortRepository) {
    val workers by repository.workers.collectAsState(initial = emptyList())
    val rules by repository.rules.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val featureStore = remember(context) { AppFeatureStore(context) }
    val specialDays by featureStore.specialDaysFlow.collectAsState(initial = emptyList())
    val payslips by featureStore.payslipsFlow.collectAsState(initial = emptyList())
    val snackbarHostState = LocalShiftFeedback.current

    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var editorDate by remember { mutableStateOf<LocalDate?>(null) }
    var pickerDate by remember { mutableStateOf<LocalDate?>(null) }
    var guidedChoice by remember { mutableStateOf<GuidedEntryChoice?>(null) }
    var editingRow by remember { mutableStateOf<ShiftWithPay?>(null) }
    var detailRow by remember { mutableStateOf<ShiftWithPay?>(null) }
    var copyDraft by remember { mutableStateOf<Triple<ShiftWithPay, ShiftEntity, Set<Long>>?>(null) }
    var pendingLockedAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val zone = remember { ZoneId.of("Europe/Rome") }
    val monthStartMillis = remember(month) { month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    val monthEndMillis = remember(month) { month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    val monthRowsFlow = remember(monthStartMillis, monthEndMillis) {
        repository.shiftRowsBetween(monthStartMillis, monthEndMillis)
    }
    val monthRows by monthRowsFlow.collectAsState(initial = emptyList())

    val historyStartMillis = remember(selectedDate) {
        selectedDate.minusYears(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val historyEndMillis = remember(selectedDate) {
        selectedDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val historyRowsFlow = remember(historyStartMillis, historyEndMillis) {
        repository.shiftRowsBetween(historyStartMillis, historyEndMillis)
    }
    val historyRows by historyRowsFlow.collectAsState(initial = emptyList())

    val rowsByDate = remember(monthRows) { monthRows.groupBy(::rowDate) }
    val dayRows = rowsByDate[selectedDate].orEmpty().sortedBy { it.shift.startEpochMillis }
    val monthTotal = monthRows.sumOf { it.pay.totalPayCents }

    fun isLocked(date: LocalDate): Boolean =
        payslips.firstOrNull { it.month == YearMonth.from(date).toString() }?.locked == true

    fun runWithMonthConfirmation(date: LocalDate, action: () -> Unit) {
        if (isLocked(date)) pendingLockedAction = action else action()
    }

    fun openCopyPicker(row: ShiftWithPay) {
        val sourceDate = rowDate(row)
        val initial = if (selectedDate != sourceDate) selectedDate else sourceDate.plusDays(1)
        DatePickerDialog(
            context,
            { _, year, monthValue, day ->
                val target = LocalDate.of(year, monthValue + 1, day)
                runWithMonthConfirmation(target) {
                    val overrideClass = specialDays.firstOrNull { it.epochDay == target.toEpochDay() }
                        ?.dayClass?.toPortDayClass()
                    val copiedIds = repeatSelectionForDate(
                        source = row,
                        rules = rules,
                        targetDate = target,
                        overrideClass = overrideClass
                    ).first
                    copyDraft = Triple(row, copyShiftToDate(row.shift, target), copiedIds)
                    detailRow = null
                }
            },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        ).show()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 92.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                MonthCalendarCard(
                    month = month,
                    selectedDate = selectedDate,
                    rowsByDate = rowsByDate,
                    onPrevious = {
                        month = month.minusMonths(1)
                        selectedDate = clampDateToMonth(selectedDate, month)
                    },
                    onNext = {
                        month = month.plusMonths(1)
                        selectedDate = clampDateToMonth(selectedDate, month)
                    },
                    onDateSelected = { selectedDate = it }
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(
                        title = "Totale mese",
                        value = money(monthTotal),
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Prestazioni",
                        value = monthRows.size.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                SectionHeader(
                    title = italianTitle(selectedDate.format(dayTitleFormatter)),
                    trailing = if (dayRows.isEmpty()) "Nessuna prestazione" else "${dayRows.size} prestaz."
                )
            }

            item {
                AnimatedContent(
                    targetState = selectedDate,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith
                            fadeOut(animationSpec = tween(140))
                    },
                    label = "Scheda giorno selezionato"
                ) { date ->
                    val selectedRows = rowsByDate[date].orEmpty()
                        .sortedBy { it.shift.startEpochMillis }
                    if (selectedRows.isEmpty()) {
                        EmptyDayCard()
                    } else {
                        DayDetailsCard(
                            rows = selectedRows,
                            onDetails = { row -> detailRow = row },
                            onEdit = { row ->
                                runWithMonthConfirmation(rowDate(row)) { editingRow = row }
                            }
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            tonalElevation = 3.dp
        ) {
            Button(
                onClick = {
                    runWithMonthConfirmation(selectedDate) { pickerDate = selectedDate }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                enabled = workers.isNotEmpty(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("＋  Aggiungi prestazione", fontWeight = FontWeight.SemiBold)
            }
        }

    }

    pendingLockedAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingLockedAction = null },
            title = { Text("Mese chiuso") },
            text = {
                Text("Questo mese è già stato chiuso dopo il controllo con la busta paga. Vuoi modificare comunque i dati?")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingLockedAction = null
                    action()
                }) { Text("Continua") }
            },
            dismissButton = {
                TextButton(onClick = { pendingLockedAction = null }) { Text("Annulla") }
            }
        )
    }

    pickerDate?.let { date ->
        val dateRows = rowsByDate[date].orEmpty()
        val hasAbsence = dateRows.any { row ->
            row.selectedRules.any { rule ->
                rule.code in setOf("ALT_FERIE", "ALT_MALATTIA", "ALT_IMA", "AVV_CONGEDO", "AVV_INAIL", "AVV_DS")
            }
        }
        val hasWorkedFirst = dateRows.any { row ->
            row.shift.performanceType == PerformanceType.TURNO &&
                row.selectedRules.none { it.code in setOf("ALT_FERIE", "ALT_MALATTIA", "ALT_IMA", "AVV_CONGEDO", "AVV_INAIL", "AVV_DS") }
        }
        val hasSecond = dateRows.any {
            it.shift.performanceType == PerformanceType.DOPPIO ||
                it.shift.performanceType == PerformanceType.MEZZO_DOPPIO
        }
        GuidedEntryPicker(
            date = date,
            dayState = guidedDayState(hasWorkedFirst, hasAbsence, hasSecond),
            rules = rules,
            onChoose = { choice ->
                guidedChoice = choice
                editorDate = date
                pickerDate = null
            },
            onDismiss = { pickerDate = null }
        )
    }

    val worker = workers.firstOrNull()
    if (editorDate != null && worker != null && guidedChoice != null) {
        ShiftEditorScreen(
            worker = worker,
            rules = rules,
            initialDate = editorDate!!,
            initialShift = null,
            initialSelectedIds = guidedInitialRuleIds(guidedChoice!!, rules),
            guidedChoice = guidedChoice,
            historyRows = historyRows,
            specialDays = specialDays,
            onDismiss = { editorDate = null; guidedChoice = null },
            onSave = { shifts, selectedIds ->
                runCatching {
                    repository.addShiftsWithSelections(shifts, selectedIds)
                    Unit
                }
            }
        )
    }

    editingRow?.let { row ->
        ShiftEditorScreen(
            worker = row.worker,
            rules = rules,
            initialDate = rowDate(row),
            initialShift = row.shift,
            initialSelectedIds = row.selectedRules.map { it.id }.toSet(),
            historyRows = historyRows,
            specialDays = specialDays,
            onDismiss = { editingRow = null },
            onSave = { shifts, selectedIds ->
                runCatching {
                    shifts.firstOrNull()?.let { shift ->
                        repository.updateShiftWithSelections(shift, selectedIds)
                    }
                    Unit
                }
            }
        )
    }

    copyDraft?.let { (source, copiedShift, copiedIds) ->
        ShiftEditorScreen(
            worker = source.worker,
            rules = rules,
            initialDate = rowDate(source),
            initialShift = copiedShift,
            initialSelectedIds = copiedIds,
            isCopy = true,
            historyRows = historyRows,
            specialDays = specialDays,
            onDismiss = { copyDraft = null },
            onSave = { shifts, selectedIds ->
                runCatching {
                    repository.addShiftsWithSelections(shifts, selectedIds)
                    Unit
                }
            }
        )
    }

    detailRow?.let { row ->
        ShiftDetailDialog(
            row = row,
            onDismiss = { detailRow = null },
            onEdit = {
                runWithMonthConfirmation(rowDate(row)) {
                    detailRow = null
                    editingRow = row
                }
            },
            onCopy = {
                openCopyPicker(row)
            },
            onDelete = {
                runWithMonthConfirmation(rowDate(row)) {
                    detailRow = null
                    scope.launch {
                        runCatching { repository.deleteShift(row.shift) }
                            .onSuccess {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Prestazione eliminata",
                                    actionLabel = "Annulla",
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Long
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    runCatching {
                                        repository.restoreDeletedShift(
                                            row.shift,
                                            row.selectedRules.map { it.id }.toSet()
                                        )
                                    }.onFailure {
                                        snackbarHostState.showSnackbar("Ripristino non riuscito.")
                                    }
                                }
                            }
                            .onFailure {
                                snackbarHostState.showSnackbar("Eliminazione non riuscita. Riprova.")
                            }
                    }
                }
            }
        )
    }
}

