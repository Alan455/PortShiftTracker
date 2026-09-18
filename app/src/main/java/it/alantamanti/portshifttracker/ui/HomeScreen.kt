package it.alantamanti.portshifttracker.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
    val rows by repository.shiftRows.collectAsState(initial = emptyList())
    val workers by repository.workers.collectAsState(initial = emptyList())
    val rules by repository.rules.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val featureStore = remember(context) { AppFeatureStore(context) }
    val specialDays by featureStore.specialDaysFlow.collectAsState(initial = emptyList())

    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var editorDate by remember { mutableStateOf<LocalDate?>(null) }
    var editingRow by remember { mutableStateOf<ShiftWithPay?>(null) }
    var detailRow by remember { mutableStateOf<ShiftWithPay?>(null) }

    val rowsByDate = remember(rows) { rows.groupBy(::rowDate) }
    val dayRows = rowsByDate[selectedDate].orEmpty().sortedBy { it.shift.startEpochMillis }
    val monthRows = remember(rows, month) { rows.filter { YearMonth.from(rowDate(it)) == month } }
    val monthTotal = monthRows.sumOf { it.pay.totalPayCents }

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

            if (dayRows.isEmpty()) {
                item { EmptyDayCard() }
            } else {
                items(dayRows, key = { it.shift.id }) { row ->
                    ShiftCompactCard(
                        row = row,
                        onDetails = { detailRow = row },
                        onEdit = { editingRow = row }
                    )
                }
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 13.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Totale giornata", fontWeight = FontWeight.SemiBold)
                            Text(
                                money(dayRows.sumOf { it.pay.totalPayCents }),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
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
                onClick = { editorDate = selectedDate },
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

    val worker = workers.firstOrNull()
    if (editorDate != null && worker != null) {
        ShiftEditorScreen(
            worker = worker,
            rules = rules,
            initialDate = editorDate!!,
            initialShift = null,
            initialSelectedIds = emptySet(),
            historyRows = rows,
            specialDays = specialDays,
            onDismiss = { editorDate = null },
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
            historyRows = rows,
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

    detailRow?.let { row ->
        ShiftDetailDialog(
            row = row,
            onDismiss = { detailRow = null },
            onEdit = {
                detailRow = null
                editingRow = row
            },
            onDelete = {
                scope.launch { repository.deleteShift(row.shift) }
                detailRow = null
            }
        )
    }
}

