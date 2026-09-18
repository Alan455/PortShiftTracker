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

private val editFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)
private val dayTitleFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN)
private val shortDayFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)

private val PortBlue = Color(0xFF0B5FBE)
private val PortBlueDark = Color(0xFF12477E)
private val PortSurface = Color(0xFFF6F8FC)
private val PortPurple = Color(0xFF7357D9)
private val PortOrange = Color(0xFFE17932)
private val PortGreen = Color(0xFF159A80)

// Voci ritirate: restano nel DB per non alterare eventuali storico/backup,
// ma non sono più selezionabili né mostrate nell'editor delle indennità.
private val retiredRuleCodes = setOf("ALT_BUON_PASTO", "ALT_CRAL", "ALT_MOD_DOPPIO")

private val portColorScheme = lightColorScheme(
    primary = PortBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEAFF),
    onPrimaryContainer = Color(0xFF082D58),
    secondary = PortGreen,
    secondaryContainer = Color(0xFFD5F4EA),
    tertiary = PortPurple,
    tertiaryContainer = Color(0xFFE9E3FF),
    surface = PortSurface,
    surfaceContainer = Color.White,
    surfaceVariant = Color(0xFFE9EEF5),
    outlineVariant = Color(0xFFD8E0EA),
    error = Color(0xFFBA1A1A)
)

private val visibleCategories = listOf(
    AllowanceCategory.TURNO,
    AllowanceCategory.MEZZO_TURNO,
    AllowanceCategory.AVVIAMENTO,
    AllowanceCategory.DISAGIO,
    AllowanceCategory.AREA,
    AllowanceCategory.DOPPIO,
    AllowanceCategory.ALTRE_VOCI,
    AllowanceCategory.ALTRO
)

private enum class MainTab(val label: String, val glyph: String) {
    HOME("Home", "⌂"),
    SUMMARY("Riepilogo", "▤"),
    RULES("Indennità", "€"),
    SETTINGS("Impostazioni", "⚙")
}

private enum class RuleFilter(val label: String) {
    TURNI("Turni"),
    DOPPI("Doppi"),
    GENERALI("Generali")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortShiftApp(repository: PortRepository) {
    var selectedTab by remember { mutableStateOf(MainTab.HOME) }

    MaterialTheme(colorScheme = portColorScheme) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = PortBlueDark,
                        titleContentColor = Color.White
                    ),
                    title = {
                        Column {
                            Text(
                                if (selectedTab == MainTab.HOME) "PortShiftTracker" else selectedTab.label,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (selectedTab == MainTab.HOME) {
                                Text(
                                    "Il tuo lavoro, i tuoi numeri",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = {
                                Text(
                                    tab.glyph,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (selectedTab) {
                    MainTab.HOME -> HomeScreen(repository)
                    MainTab.SUMMARY -> SummaryScreen(repository)
                    MainTab.RULES -> RulesScreen(repository)
                    MainTab.SETTINGS -> SettingsScreen(repository)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(repository: PortRepository) {
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

@Composable
private fun MonthCalendarCard(
    month: YearMonth,
    selectedDate: LocalDate,
    rowsByDate: Map<LocalDate, List<ShiftWithPay>>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    val firstOffset = month.atDay(1).dayOfWeek.value - 1
    val requiredCells = firstOffset + month.lengthOfMonth()
    val weekCount = (requiredCells + 6) / 7
    val cells = (0 until weekCount * 7).map { index ->
        val day = index - firstOffset + 1
        if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onPrevious),
                    contentAlignment = Alignment.Center
                ) {
                    Text("‹", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    italianTitle(month.format(monthFormatter)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onNext),
                    contentAlignment = Alignment.Center
                ) {
                    Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            Row(Modifier.fillMaxWidth()) {
                listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom").forEach { label ->
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        Box(
                            modifier = Modifier.weight(1f).height(44.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (date != null) {
                                CalendarDay(
                                    date = date,
                                    selected = date == selectedDate,
                                    dayRows = rowsByDate[date].orEmpty(),
                                    onClick = { onDateSelected(date) }
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
private fun CalendarDay(
    date: LocalDate,
    selected: Boolean,
    dayRows: List<ShiftWithPay>,
    onClick: () -> Unit
) {
    val today = date == LocalDate.now()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(28.dp).clickable(onClick = onClick),
            shape = CircleShape,
            color = when {
                selected -> MaterialTheme.colorScheme.primary
                today -> MaterialTheme.colorScheme.primaryContainer
                else -> Color.Transparent
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (selected || today) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (dayRows.isNotEmpty()) {
            Text(
                dayRows.take(2).joinToString(" ") { calendarShiftCode(it) },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyDayCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp, vertical = 16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Nessuna prestazione registrata", fontWeight = FontWeight.SemiBold)
            Text(
                "Usa il pulsante qui sotto per aggiungere un turno, un doppio o un mezzo doppio.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ShiftCompactCard(
    row: ShiftWithPay,
    onDetails: () -> Unit,
    onEdit: () -> Unit
) {
    val start = rowStart(row)
    val end = rowEnd(row)
    val accent = performanceColor(row.shift.performanceType)
    val turnRule = row.selectedRules.firstOrNull {
        it.category == AllowanceCategory.TURNO ||
            it.category == AllowanceCategory.DOPPIO ||
            it.category == AllowanceCategory.MEZZO_TURNO
    }
    val secondaryRules = row.selectedRules
        .filterNot { it.id == turnRule?.id }
        .take(4)
        .joinToString(" · ") { it.name }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(5.dp)
                    .height(62.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    buildString {
                        append(performanceLabel(row.shift.performanceType))
                        turnRule?.let { append(" • ${it.name}") }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${start.format(timeFormatter)}–${end.format(timeFormatter)}" +
                        row.shift.role.takeIf { it.isNotBlank() }?.let { "  •  $it" }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (secondaryRules.isNotBlank()) {
                    Text(
                        secondaryRules,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onDetails) { Text("Dettagli") }
                    TextButton(onClick = onEdit) { Text("Modifica") }
                }
            }
            Text(
                money(row.pay.totalPayCents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ShiftDetailDialog(
    row: ShiftWithPay,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(performanceLabel(row.shift.performanceType))
                Text(
                    italianTitle(rowDate(row).format(dayTitleFormatter)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 480.dp)) {
                item {
                    BreakdownLine("Base", row.pay.basePayCents, bold = true)
                }
                items(row.pay.allowanceLines, key = { it.ruleId }) { line ->
                    BreakdownLine(line.name, line.amountCents)
                }
                item { HorizontalDivider() }
                item { BreakdownLine("Totale", row.pay.totalPayCents, bold = true, primary = true) }
                if (row.shift.notes.isNotBlank()) {
                    item {
                        Text("Note", fontWeight = FontWeight.SemiBold)
                        Text(row.shift.notes, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onEdit) { Text("Modifica") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Chiudi") }
            }
        }
    )
}

@Composable
private fun BreakdownLine(label: String, cents: Long, bold: Boolean = false, primary: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
        Text(
            money(cents),
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShiftEditorScreen(
    worker: WorkerEntity,
    rules: List<AllowanceRuleEntity>,
    initialDate: LocalDate,
    initialShift: ShiftEntity?,
    initialSelectedIds: Set<Long>,
    historyRows: List<ShiftWithPay>,
    specialDays: List<SpecialDayOverride>,
    onDismiss: () -> Unit,
    onSave: suspend (List<ShiftEntity>, Set<Long>) -> Result<Unit>
) {
    val context = LocalContext.current
    val initialZone = ZoneId.of(initialShift?.zoneId ?: "Europe/Rome")
    val initialStart = initialShift?.let {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(it.startEpochMillis), initialZone)
    } ?: initialDate.atTime(8, 0)
    val initialEnd = initialShift?.let {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(it.endEpochMillis), initialZone)
    } ?: initialStart.plusHours(6)

    var startText by remember(initialShift?.id, initialDate) { mutableStateOf(initialStart.format(editFormatter)) }
    var endText by remember(initialShift?.id, initialDate) { mutableStateOf(initialEnd.format(editFormatter)) }
    var role by remember(initialShift?.id) { mutableStateOf(initialShift?.role ?: "Operatore") }
    var notes by remember(initialShift?.id) { mutableStateOf(initialShift?.notes.orEmpty()) }
    var performanceType by remember(initialShift?.id) { mutableStateOf(initialShift?.performanceType ?: PerformanceType.TURNO) }
    var selectedIds by remember(initialShift?.id) { mutableStateOf(initialSelectedIds) }
    var rangeEndDate by remember(initialShift?.id, initialDate) { mutableStateOf(initialDate) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showNotes by remember(initialShift?.id) { mutableStateOf(initialShift?.notes?.isNotBlank() == true) }
    var showBreakdown by remember(initialShift?.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var quickKind by remember(initialShift?.id, initialSelectedIds, rules) {
        mutableStateOf(
            inferQuickShiftKind(
                initialSelectedIds,
                rules,
                initialShift?.performanceType ?: PerformanceType.TURNO
            )
        )
    }
    val editorDate = runCatching { LocalDateTime.parse(startText, editFormatter).toLocalDate() }.getOrDefault(initialDate)
    val specialOverrideClass = specialDays.firstOrNull { it.epochDay == editorDate.toEpochDay() }
        ?.dayClass?.toPortDayClass()
    // Turno ordinario e Doppio usano sempre la selezione rapida basata sulla
    // data scelta nel calendario. Solo il Mezzo Doppio mantiene data/orario.
    val effectiveQuickMode =
        performanceType == PerformanceType.TURNO || performanceType == PerformanceType.DOPPIO
    val calculator = remember { AllowanceCalculator() }

    val manualRules = rules.filter {
        it.enabled &&
            it.code !in retiredRuleCodes &&
            it.applicationMode == AllowanceApplicationMode.MANUAL &&
            (it.performanceMask and performanceType.maskBit) != 0
    }
    val normalizedSelectedIds = normalizeSelectedRuleIds(performanceType, selectedIds, rules)
    LaunchedEffect(normalizedSelectedIds) {
        if (normalizedSelectedIds != selectedIds) selectedIds = normalizedSelectedIds
    }
    val selectedRules = manualRules.filter { it.id in normalizedSelectedIds }
    val saveValidationMessage = selectionValidationMessage(performanceType, normalizedSelectedIds, rules)
    val usageScores = remember(historyRows, role, performanceType, editorDate) {
        ruleUsageScores(historyRows, editorDate, performanceType, role)
    }
    val selectedSummary = selectionSummary(normalizedSelectedIds, rules, performanceType)
    val absenceRule = selectedRules.firstOrNull { it.code == "ALT_FERIE" || it.code == "ALT_MALATTIA" }
    val rangeEnabled = initialShift == null && absenceRule != null
    val effectiveRangeEnd = if (rangeEndDate.isBefore(editorDate)) editorDate else rangeEndDate
    val selectedTags = selectedRules.flatMap { parseTags(it.tagsCsv) }.toSet()
    val relationWarnings = selectedRules.mapNotNull { rule ->
        val recommendedTags = parseTags(rule.recommendedWithAnyTagCsv)
        if (recommendedTags.isNotEmpty() && recommendedTags.none { it in selectedTags }) {
            when (rule.code) {
                "ALT_MEZZA_IMA" -> "Mezza IMA normalmente va insieme a TUMezzo o ONmezzo."
                else -> "${rule.name}: è normalmente associata a ${recommendedTags.joinToString()}."
            }
        } else null
    }
    val suggestedCompanions = manualRules.filter { rule ->
        rule.id !in normalizedSelectedIds && parseTags(rule.recommendedWithAnyTagCsv).any { it in selectedTags }
    }
    val coherenceWarnings = consistencyWarnings(performanceType, rules.filter { it.id in normalizedSelectedIds })

    val draftShift = remember(startText, endText, role, notes, performanceType, initialShift?.id) {
        parseShiftOrNull(
            id = initialShift?.id ?: 0,
            workerId = worker.id,
            startText = startText,
            endText = endText,
            role = role,
            notes = notes,
            performanceType = performanceType
        )
    }
    val preview = runCatching {
        draftShift?.let {
            calculator.calculate(worker.toDomain(), it.toDomain(), rules.map { rule -> rule.toDomain() }, normalizedSelectedIds)
        }
    }.getOrNull()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = PortBlueDark,
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White
                        ),
                        navigationIcon = {
                            TextButton(
                                onClick = onDismiss,
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                            ) {
                                Text("‹", style = MaterialTheme.typography.headlineSmall)
                            }
                        },
                        title = {
                            Column {
                                Text(if (initialShift == null) "Nuova prestazione" else "Modifica prestazione")
                                Text(
                                    italianTitle(editorDate.format(shortDayFormatter)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.82f)
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    Surface(color = Color.White, tonalElevation = 8.dp) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        selectedSummary,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    preview?.let {
                                        Text(
                                            "Base ${money(it.basePayCents)}  •  Indennità ${money(it.allowancesCents)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            payFormula(it),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    preview?.let { money(it.totalPayCents) } ?: "—",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Button(
                                onClick = {
                                    val shift = parseShiftOrNull(
                                        id = initialShift?.id ?: 0,
                                        workerId = worker.id,
                                        startText = startText,
                                        endText = endText,
                                        role = role,
                                        notes = notes,
                                        performanceType = performanceType
                                    )
                                    when {
                                        shift == null -> {
                                            error = "Controlla data e orari: la fine deve essere successiva all'inizio."
                                        }
                                        saveValidationMessage != null -> {
                                            error = saveValidationMessage
                                        }
                                        else -> {
                                            error = null
                                            val shiftsToSave = if (rangeEnabled) {
                                                expandShiftRange(shift, effectiveRangeEnd)
                                            } else {
                                                listOf(shift)
                                            }
                                            saving = true
                                            scope.launch {
                                                val result = onSave(shiftsToSave, normalizedSelectedIds)
                                                saving = false
                                                result.onSuccess { onDismiss() }
                                                    .onFailure { failure ->
                                                        error = when (failure) {
                                                            is DuplicatePerformanceException ->
                                                                "Esiste già una ${performanceLabel(failure.performanceType)} il ${italianTitle(failure.date.format(shortDayFormatter))}."
                                                            else -> failure.message ?: "Errore durante il salvataggio."
                                                        }
                                                    }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !saving && saveValidationMessage == null,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    when {
                                        saving -> "Salvataggio…"
                                        initialShift != null -> "Salva modifiche"
                                        rangeEnabled -> "Salva periodo ${absenceRule.name}"
                                        else -> "Salva prestazione"
                                    },
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                LazyColumn(
                    Modifier.padding(padding).fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        top = 12.dp,
                        end = 16.dp,
                        bottom = 132.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        EditorSectionCard(title = "1. Tipo di prestazione") {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PerformanceType.entries.forEach { type ->
                                    val selected = performanceType == type
                                    val shape = RoundedCornerShape(14.dp)
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 74.dp)
                                            .border(
                                                width = if (selected) 2.dp else 1.dp,
                                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                shape = shape
                                            )
                                            .clickable {
                                                performanceType = type
                                                val compatibleIds = selectedIds.filterTo(mutableSetOf()) { id ->
                                                    rules.firstOrNull { it.id == id }
                                                        ?.let { (it.performanceMask and type.maskBit) != 0 } == true
                                                }
                                                selectedIds = normalizeSelectedRuleIds(type, compatibleIds, rules)
                                                quickKind = inferQuickShiftKind(selectedIds, rules, type)
                                            },
                                        shape = shape,
                                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                    ) {
                                        Column(
                                            Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                when (type) {
                                                    PerformanceType.TURNO -> "T"
                                                    PerformanceType.DOPPIO -> "2×"
                                                    PerformanceType.MEZZO_DOPPIO -> "½×"
                                                },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                performanceLabel(type),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            InfoPanel(performanceInfo(worker, performanceType))
                        }
                    }

                    if (effectiveQuickMode) {
                        item {
                            QuickShiftPanel(
                                date = editorDate,
                                rules = rules,
                                selectedKind = quickKind,
                                performanceType = performanceType,
                                doubleBaseCents = worker.doubleBaseCents,
                                overrideClass = specialOverrideClass,
                                onKindSelected = { kind ->
                                    quickKind = kind
                                    selectedIds = normalizeSelectedRuleIds(
                                        performanceType,
                                        applyQuickTurnSelection(
                                            currentIds = selectedIds,
                                            rules = rules,
                                            kind = kind,
                                            date = editorDate,
                                            overrideClass = specialOverrideClass,
                                            performanceType = performanceType
                                        ),
                                        rules
                                    )
                                }
                            )
                        }
                    }

                    preview?.let { pay ->
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Totale provvisorio", style = MaterialTheme.typography.labelMedium)
                                        Text(
                                            selectedSummary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            payFormula(pay),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        money(pay.totalPayCents),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    if (!effectiveQuickMode) {
                        item {
                            val currentStart = runCatching { LocalDateTime.parse(startText, editFormatter) }.getOrDefault(initialStart)
                            val currentEnd = runCatching { LocalDateTime.parse(endText, editFormatter) }.getOrDefault(initialEnd)

                        EditorSectionCard(title = "2. Data e orario") {
                            PickerField(
                                label = "Data",
                                value = italianTitle(currentStart.toLocalDate().format(shortDayFormatter)),
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    val duration = java.time.Duration.between(currentStart, currentEnd)
                                        .takeIf { !it.isNegative && !it.isZero }
                                        ?: java.time.Duration.ofHours(6)
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, day ->
                                            val newStart = LocalDateTime.of(
                                                LocalDate.of(year, month + 1, day),
                                                currentStart.toLocalTime()
                                            )
                                            startText = newStart.format(editFormatter)
                                            endText = newStart.plus(duration).format(editFormatter)
                                            if (performanceType == PerformanceType.TURNO) {
                                                quickKind?.let { kind ->
                                                    val newDate = newStart.toLocalDate()
                                                    val override = specialDays.firstOrNull { it.epochDay == newDate.toEpochDay() }
                                                        ?.dayClass?.toPortDayClass()
                                                    selectedIds = applyQuickTurnSelection(
                                                        selectedIds,
                                                        rules,
                                                        kind,
                                                        newDate,
                                                        override
                                                    )
                                                }
                                            }
                                        },
                                        currentStart.year,
                                        currentStart.monthValue - 1,
                                        currentStart.dayOfMonth
                                    ).show()
                                }
                            )

                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PickerField(
                                    label = "Ora inizio",
                                    value = currentStart.format(timeFormatter),
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        val duration = java.time.Duration.between(currentStart, currentEnd)
                                            .takeIf { !it.isNegative && !it.isZero }
                                            ?: java.time.Duration.ofHours(6)
                                        TimePickerDialog(
                                            context,
                                            { _, hour, minute ->
                                                val newStart = LocalDateTime.of(
                                                    currentStart.toLocalDate(),
                                                    LocalTime.of(hour, minute)
                                                )
                                                startText = newStart.format(editFormatter)
                                                endText = newStart.plus(duration).format(editFormatter)
                                            },
                                            currentStart.hour,
                                            currentStart.minute,
                                            true
                                        ).show()
                                    }
                                )
                                PickerField(
                                    label = "Ora fine",
                                    value = currentEnd.format(timeFormatter) + if (currentEnd.toLocalDate().isAfter(currentStart.toLocalDate())) " +1g" else "",
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        TimePickerDialog(
                                            context,
                                            { _, hour, minute ->
                                                var candidate = LocalDateTime.of(
                                                    currentStart.toLocalDate(),
                                                    LocalTime.of(hour, minute)
                                                )
                                                if (!candidate.isAfter(currentStart)) candidate = candidate.plusDays(1)
                                                endText = candidate.format(editFormatter)
                                            },
                                            currentEnd.hour,
                                            currentEnd.minute,
                                            true
                                        ).show()
                                    }
                                )
                            }

                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Durata rapida", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    java.time.Duration.between(currentStart, currentEnd)
                                        .takeIf { !it.isNegative }
                                        ?.let { d -> "${d.toHours()}h ${d.toMinutesPart()}m" }
                                        ?: "—",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(4L, 6L, 8L).forEach { hours ->
                                    OutlinedButton(
                                        onClick = { endText = currentStart.plusHours(hours).format(editFormatter) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("$hours h")
                                    }
                                }
                            }
                        }
                    }
                    }

                    item {
                        EditorSectionCard(title = "Mansione e note") {
                            OutlinedTextField(
                                value = role,
                                onValueChange = { role = it },
                                label = { Text("Mansione") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(Modifier.height(4.dp))
                            if (!showNotes) {
                                TextButton(onClick = { showNotes = true }) { Text("＋ Aggiungi note") }
                            } else {
                                OutlinedTextField(
                                    value = notes,
                                    onValueChange = { notes = it },
                                    label = { Text("Note") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2
                                )
                                TextButton(
                                    onClick = {
                                        notes = ""
                                        showNotes = false
                                    }
                                ) { Text("Rimuovi note") }
                            }
                        }
                    }

                    item {
                        PresetQuickBar(
                            rules = rules,
                            selectedIds = selectedIds,
                            role = role,
                            performanceType = performanceType,
                            onApply = { presetRole, ids ->
                                role = presetRole
                                selectedIds = normalizeSelectedRuleIds(performanceType, ids, rules)
                            }
                        )
                    }

                    item {
                        SectionHeader(
                            title = if (effectiveQuickMode) "2. Indennità" else "3. Indennità",
                            trailing = selectedSummary
                        )
                    }

                    categoriesForPerformance(performanceType)
                        .filterNot { category ->
                            effectiveQuickMode && (
                                category == AllowanceCategory.TURNO ||
                                    category == AllowanceCategory.DOPPIO
                                )
                        }
                        .forEach { category ->
                        val categoryRules = manualRules.filter { it.category == category }
                        if (categoryRules.isNotEmpty()) {
                            item {
                                AllowanceCategoryCard(
                                    category = category,
                                    rules = categoryRules,
                                    selectedIds = selectedIds,
                                    performanceType = performanceType,
                                    usageCounts = usageScores,
                                    onToggle = { rule, checked ->
                                        selectedIds = normalizeSelectedRuleIds(
                                            performanceType,
                                            toggleRule(selectedIds, rule, manualRules, checked),
                                            rules
                                        )
                                    }
                                )
                            }
                        }
                    }

                    if (initialShift == null) {
                        absenceRule?.let { selectedAbsence ->
                            item {
                                EditorSectionCard(title = "${selectedAbsence.name}: periodo") {
                                    Text(
                                        "Dal ${italianTitle(editorDate.format(shortDayFormatter))}. Scegli l'ultimo giorno del periodo.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    PickerField(
                                        label = "Fino al",
                                        value = italianTitle(effectiveRangeEnd.format(shortDayFormatter)),
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            val current = effectiveRangeEnd
                                            DatePickerDialog(
                                                context,
                                                { _, year, month, day ->
                                                    val picked = LocalDate.of(year, month + 1, day)
                                                    rangeEndDate = if (picked.isBefore(editorDate)) editorDate else picked
                                                },
                                                current.year,
                                                current.monthValue - 1,
                                                current.dayOfMonth
                                            ).show()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (suggestedCompanions.isNotEmpty()) {
                        item {
                            EditorSectionCard(title = "Suggerite dalla selezione") {
                                Text(
                                    "Puoi aggiungerle con un tocco.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    suggestedCompanions.take(6).forEach { rule ->
                                        OutlinedButton(
                                            onClick = { selectedIds = normalizeSelectedRuleIds(
                                                performanceType,
                                                toggleRule(selectedIds, rule, manualRules, true),
                                                rules
                                            ) }
                                        ) {
                                            Text("＋ ${rule.name}")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    coherenceWarnings.forEach { warning ->
                        item { WarningPanel(warning) }
                    }
                    relationWarnings.forEach { warning ->
                        item { WarningPanel(warning) }
                    }

                    saveValidationMessage?.let { message ->
                        item { WarningPanel(message) }
                    }
                    error?.let { message ->
                        item { WarningPanel(message) }
                    }

                    if (preview != null) {
                        item {
                            TextButton(
                                onClick = { showBreakdown = !showBreakdown },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (showBreakdown) "Nascondi dettaglio calcolo" else "Vedi dettaglio calcolo")
                            }
                        }
                    }

                    if (showBreakdown) {
                        preview?.let { pay ->
                            item {
                                EditorSectionCard(title = "Dettaglio calcolo") {
                                    BreakdownLine("Base", pay.basePayCents)
                                    pay.allowanceLines.forEach { line ->
                                        BreakdownLine(line.name, line.amountCents)
                                    }
                                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                    BreakdownLine("Totale", pay.totalPayCents, bold = true, primary = true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = modifier
            .heightIn(min = 62.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EditorSectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun AllowanceCategoryCard(
    category: AllowanceCategory,
    rules: List<AllowanceRuleEntity>,
    selectedIds: Set<Long>,
    performanceType: PerformanceType,
    usageCounts: Map<Long, Int>,
    onToggle: (AllowanceRuleEntity, Boolean) -> Unit
) {
    val orderedRules = rules.sortedWith(
        compareByDescending<AllowanceRuleEntity> { usageCounts[it.id] ?: 0 }
            .thenBy { it.priority }
            .thenBy { it.name }
    )
    val selectedCount = orderedRules.count { it.id in selectedIds }
    val horizontal = category == AllowanceCategory.AVVIAMENTO ||
        category == AllowanceCategory.DISAGIO ||
        category == AllowanceCategory.AREA

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    categoryEditorTitle(category, performanceType),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (selectedCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            "$selectedCount scelte",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            when (category) {
                AllowanceCategory.AREA -> Text(
                    "Se prevista, registra l'area insieme all'avviamento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AllowanceCategory.DOPPIO -> Text(
                    if (performanceType == PerformanceType.MEZZO_DOPPIO)
                        "Nel Mezzo Doppio si dimezza solo l'indennità di turno; Area e Disagi restano interi."
                    else
                        "La base Doppio è separata: qui scegli la relativa indennità di turno.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> Unit
            }

            if (horizontal) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(orderedRules, key = { it.id }) { rule ->
                        AllowanceRuleTile(
                            rule = rule,
                            checked = rule.id in selectedIds,
                            performanceType = performanceType,
                            modifier = Modifier.width(156.dp),
                            onToggle = onToggle
                        )
                    }
                }
            } else {
                orderedRules.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { rule ->
                            AllowanceRuleTile(
                                rule = rule,
                                checked = rule.id in selectedIds,
                                performanceType = performanceType,
                                modifier = Modifier.weight(1f),
                                onToggle = onToggle
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AllowanceRuleTile(
    rule: AllowanceRuleEntity,
    checked: Boolean,
    performanceType: PerformanceType,
    modifier: Modifier,
    onToggle: (AllowanceRuleEntity, Boolean) -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = modifier
            .heightIn(min = 78.dp)
            .border(
                width = if (checked) 2.dp else 1.dp,
                color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
            .clickable { onToggle(rule, !checked) },
        shape = shape,
        color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    rule.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (checked) FontWeight.Bold else FontWeight.SemiBold
                )
                if (checked) {
                    Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                ruleValueLabel(rule, performanceType),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            if (checked) {
                Text(
                    ruleDescription(rule),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InfoPanel(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "ⓘ  $text",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun WarningPanel(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "⚠  $text",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun SummaryScreen(repository: PortRepository) {
    val rows by repository.shiftRows.collectAsState(initial = emptyList())
    val rules by repository.rules.collectAsState(initial = emptyList())
    var month by remember { mutableStateOf(YearMonth.now()) }

    val monthRows = remember(rows, month) { rows.filter { YearMonth.from(rowDate(it)) == month } }
    val total = monthRows.sumOf { it.pay.totalPayCents }
    val baseTotal = monthRows.sumOf { it.pay.basePayCents }
    val allowanceTotal = monthRows.sumOf { it.pay.allowancesCents }
    val daysWorked = monthRows.map(::rowDate).distinct().size
    val groupedDays = monthRows.groupBy(::rowDate).toList().sortedByDescending { it.first }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Row(
                    Modifier.padding(10.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
                    Text(
                        italianTitle(month.format(monthFormatter)),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = { month = month.plusMonths(1) }) { Text("›") }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Totale mese", style = MaterialTheme.typography.labelLarge)
                    Text(
                        money(total),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Base ${money(baseTotal)}  •  Indennità ${money(allowanceTotal)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Giorni lavorati", daysWorked.toString(), Modifier.weight(1f))
                MetricCard("Prestazioni", monthRows.size.toString(), Modifier.weight(1f))
            }
        }

        item { SectionHeader("Totali per tipo di prestazione") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PerformanceType.entries.forEach { type ->
                        val matching = monthRows.filter { it.shift.performanceType == type }
                        PerformanceTotalRow(type, matching.size, matching.sumOf { it.pay.totalPayCents })
                    }
                }
            }
        }

        item { PayslipComparisonCard(month = month, rows = monthRows, rules = rules) }
        item { MonthlyExportCard(month = month, rows = monthRows) }

        item { SectionHeader("Giornate del mese") }
        if (groupedDays.isEmpty()) {
            item { EmptySummaryCard() }
        } else {
            items(groupedDays, key = { it.first }) { (date, dayRows) ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(italianTitle(date.format(shortDayFormatter)), fontWeight = FontWeight.SemiBold)
                            Text(money(dayRows.sumOf { it.pay.totalPayCents }), fontWeight = FontWeight.Bold)
                        }
                        dayRows.forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    performanceLabel(row.shift.performanceType) +
                                        mainAllowanceName(row)?.let { " • $it" }.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(money(row.pay.totalPayCents), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformanceTotalRow(type: PerformanceType, count: Int, totalCents: Long) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(performanceColor(type)))
        Spacer(Modifier.width(10.dp))
        Text(performanceLabel(type), Modifier.weight(1f))
        Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(18.dp))
        Text(money(totalCents), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptySummaryCard() {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Text(
            "Nessuna prestazione registrata in questo mese.",
            Modifier.padding(18.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RulesScreen(repository: PortRepository) {
    val rules by repository.rules.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(RuleFilter.TURNI) }
    var search by remember { mutableStateOf("") }
    var editor by remember { mutableStateOf<AllowanceRuleEntity?>(null) }
    var createNew by remember { mutableStateOf(false) }

    val categories = when (filter) {
        RuleFilter.TURNI -> setOf(AllowanceCategory.TURNO, AllowanceCategory.MEZZO_TURNO)
        RuleFilter.DOPPI -> setOf(AllowanceCategory.DOPPIO)
        RuleFilter.GENERALI -> setOf(
            AllowanceCategory.AVVIAMENTO,
            AllowanceCategory.DISAGIO,
            AllowanceCategory.AREA,
            AllowanceCategory.ALTRE_VOCI,
            AllowanceCategory.ALTRO
        )
    }
    val filteredRules = rules.filter {
        it.code !in retiredRuleCodes &&
            it.category in categories &&
            (search.isBlank() || it.name.contains(search, ignoreCase = true) || it.code.contains(search, ignoreCase = true))
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RuleFilter.entries.forEach { tab ->
                    FilterChip(selected = filter == tab, onClick = { filter = tab }, label = { Text(tab.label) })
                }
            }
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Cerca indennità") },
                leadingIcon = { Text("⌕") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(onClick = { createNew = true }, modifier = Modifier.fillMaxWidth()) {
                Text("＋  Nuova voce")
            }
        }

        if (filteredRules.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Text("Nessuna voce trovata.", Modifier.padding(18.dp))
                }
            }
        } else {
            val grouped = filteredRules.groupBy { it.category }
            visibleCategories.filter { it in grouped.keys }.forEach { category ->
                item { SectionHeader(categoryLabel(category)) }
                items(grouped[category].orEmpty(), key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        onEnabledChanged = { enabled -> scope.launch { repository.saveRule(rule.copy(enabled = enabled)) } },
                        onEdit = { editor = rule }
                    )
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (createNew || editor != null) {
        RuleEditorDialog(
            initial = editor,
            onDismiss = { createNew = false; editor = null },
            onSave = { rule ->
                scope.launch { repository.saveRule(rule) }
                createNew = false
                editor = null
            }
        )
    }
}

@Composable
private fun RuleCard(
    rule: AllowanceRuleEntity,
    onEnabledChanged: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(rule.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    ruleDescription(rule),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (rule.applicationMode == AllowanceApplicationMode.MANUAL) "Selezione manuale" else "Automatica",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Switch(checked = rule.enabled, onCheckedChange = onEnabledChanged)
            TextButton(onClick = onEdit) { Text("Modifica") }
        }
    }
}

@Composable
private fun RuleEditorDialog(
    initial: AllowanceRuleEntity?,
    onDismiss: () -> Unit,
    onSave: (AllowanceRuleEntity) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var code by remember(initial) { mutableStateOf(initial?.code ?: "CUSTOM_${System.currentTimeMillis()}") }
    var type by remember(initial) { mutableStateOf(initial?.calculationType ?: AllowanceCalculationType.FIXED_PER_SHIFT) }
    var category by remember(initial) { mutableStateOf(initial?.category ?: AllowanceCategory.ALTRE_VOCI) }
    var applicationMode by remember(initial) { mutableStateOf(initial?.applicationMode ?: AllowanceApplicationMode.MANUAL) }
    var performanceMask by remember(initial) {
        mutableIntStateOf(initial?.performanceMask ?: PerformanceType.entries.fold(0) { acc, performance -> acc or performance.maskBit })
    }
    var valueText by remember(initial) { mutableStateOf(valueForEditor(initial)) }
    var useWindow by remember(initial) { mutableStateOf(initial?.windowStartMinute != null) }
    var startHour by remember(initial) { mutableStateOf(minutesToText(initial?.windowStartMinute ?: 22 * 60)) }
    var endHour by remember(initial) { mutableStateOf(minutesToText(initial?.windowEndMinute ?: 6 * 60)) }
    var weekdayMask by remember(initial) { mutableIntStateOf(initial?.weekdayMask ?: 127) }
    var roleFilter by remember(initial) { mutableStateOf(initial?.roleFilter.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuova indennità" else "Modifica indennità") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.heightIn(max = 560.dp)) {
                item { OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(code, { code = it }, label = { Text("Codice") }, enabled = initial == null, modifier = Modifier.fillMaxWidth()) }
                item { Text("Categoria", fontWeight = FontWeight.SemiBold) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(visibleCategories) { c ->
                            FilterChip(selected = category == c, onClick = { category = c }, label = { Text(categoryShortLabel(c)) })
                        }
                    }
                }
                item { Text("Applicazione", fontWeight = FontWeight.SemiBold) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AllowanceApplicationMode.entries.forEach { mode ->
                            FilterChip(
                                selected = applicationMode == mode,
                                onClick = { applicationMode = mode },
                                label = { Text(if (mode == AllowanceApplicationMode.MANUAL) "Manuale" else "Auto") }
                            )
                        }
                    }
                }
                item { Text("Prestazioni compatibili", fontWeight = FontWeight.SemiBold) }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PerformanceType.entries.forEach { performance ->
                            FilterChip(
                                selected = (performanceMask and performance.maskBit) != 0,
                                onClick = { performanceMask = performanceMask xor performance.maskBit },
                                label = { Text(performanceLabel(performance)) }
                            )
                        }
                    }
                }
                item { Text("Tipo di calcolo", fontWeight = FontWeight.SemiBold) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AllowanceCalculationType.entries.forEach { calculation ->
                            FilterChip(selected = type == calculation, onClick = { type = calculation }, label = { Text(typeLabel(calculation)) })
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = valueText,
                        onValueChange = { valueText = it },
                        label = { Text(if (type == AllowanceCalculationType.PERCENT_BASE) "Percentuale" else "Importo €") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = useWindow, onCheckedChange = { useWindow = it })
                        Text("Limita a fascia oraria")
                    }
                }
                if (useWindow) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(startHour, { startHour = it }, modifier = Modifier.weight(1f), label = { Text("Da HH:mm") })
                            OutlinedTextField(endHour, { endHour = it }, modifier = Modifier.weight(1f), label = { Text("A HH:mm") })
                        }
                    }
                }
                item { Text("Giorni applicabili", fontWeight = FontWeight.SemiBold) }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("L", "M", "M", "G", "V", "S", "D").forEachIndexed { index, label ->
                            FilterChip(
                                selected = (weekdayMask and (1 shl index)) != 0,
                                onClick = { weekdayMask = weekdayMask xor (1 shl index) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
                item { OutlinedTextField(roleFilter, { roleFilter = it }, label = { Text("Mansioni opzionali, separate da virgola") }, modifier = Modifier.fillMaxWidth()) }
                error?.let { item { WarningPanel(it) } }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    require(name.isNotBlank() && code.isNotBlank())
                    val numeric = valueText.replace(',', '.').toDouble()
                    val storedValue = (numeric * 100).roundToLong()
                    val startMin = if (useWindow) parseTimeMinutes(startHour) else null
                    val endMin = if (useWindow) parseTimeMinutes(endHour) else null
                    require(weekdayMask != 0)
                    require(performanceMask != 0)
                    AllowanceRuleEntity(
                        id = initial?.id ?: 0,
                        name = name,
                        code = code,
                        calculationType = type,
                        value = storedValue,
                        enabled = initial?.enabled ?: true,
                        weekdayMask = weekdayMask,
                        windowStartMinute = startMin,
                        windowEndMinute = endMin,
                        minimumShiftMinutes = initial?.minimumShiftMinutes ?: 0,
                        roleFilter = roleFilter.ifBlank { null },
                        priority = initial?.priority ?: 1000,
                        effectiveFromEpochDay = initial?.effectiveFromEpochDay,
                        effectiveToEpochDay = initial?.effectiveToEpochDay,
                        category = category,
                        applicationMode = applicationMode,
                        exclusiveGroup = initial?.exclusiveGroup,
                        basePayEffect = initial?.basePayEffect ?: it.alantamanti.portshifttracker.domain.BasePayEffect.ADDITIVE,
                        autoTrigger = initial?.autoTrigger ?: it.alantamanti.portshifttracker.domain.AllowanceAutoTrigger.NONE,
                        turnAllowanceMultiplierBasisPoints = initial?.turnAllowanceMultiplierBasisPoints ?: 10000,
                        tagsCsv = initial?.tagsCsv,
                        recommendedWithAnyTagCsv = initial?.recommendedWithAnyTagCsv,
                        performanceMask = performanceMask
                    )
                }.onSuccess(onSave).onFailure { error = "Controlla nome, codice, importo, giorni e orari." }
            }) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

@Composable
private fun SettingsScreen(repository: PortRepository) {
    val workers by repository.workers.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val worker = workers.firstOrNull()

    var name by remember(worker?.id, worker?.name) { mutableStateOf(worker?.name ?: "") }
    var mode by remember(worker?.id, worker?.basePayMode) { mutableStateOf(worker?.basePayMode ?: BasePayMode.FIXED_PER_SHIFT) }
    var fixedBase by remember(worker?.id, worker?.baseShiftCents) { mutableStateOf(worker?.baseShiftCents?.toEuroText() ?: "67.80") }
    var hourlyRate by remember(worker?.id, worker?.hourlyRateCents) { mutableStateOf(worker?.hourlyRateCents?.toEuroText() ?: "0.00") }
    var doubleBase by remember(worker?.id, worker?.doubleBaseCents) { mutableStateOf(worker?.doubleBaseCents?.toEuroText() ?: "88.40") }
    var irpef by remember(worker?.id, worker?.irpefBasisPoints) { mutableStateOf(worker?.irpefBasisPoints?.let { "%.2f".format(Locale.US, it / 100.0) } ?: "30.00") }
    var scatti by remember(worker?.id, worker?.senioritySteps) { mutableStateOf((worker?.senioritySteps ?: 3).toString()) }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    val doubleCentsPreview = doubleBase.replace(',', '.').toDoubleOrNull()?.times(100)?.roundToLong() ?: 8840

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsCard("Profilo lavoratore") {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        }

        item {
            SettingsCard("Tariffe base") {
                Text("Calcolo paga base", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == BasePayMode.FIXED_PER_SHIFT, onClick = { mode = BasePayMode.FIXED_PER_SHIFT }, label = { Text("Turno fisso") })
                    FilterChip(selected = mode == BasePayMode.HOURLY, onClick = { mode = BasePayMode.HOURLY }, label = { Text("Oraria") })
                }
                Spacer(Modifier.height(8.dp))
                SettingsMoneyField("Base turno", fixedBase, { fixedBase = it })
                SettingsMoneyField("Paga base oraria", hourlyRate, { hourlyRate = it })
                SettingsMoneyField("Base Doppio", doubleBase, { doubleBase = it })
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Base Mezzo Doppio")
                        Text(money((doubleCentsPreview / 2.0).roundToLong()), fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(
                    "Il Mezzo Doppio è sempre il 50% della base Doppio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SettingsCard("Altri parametri") {
                OutlinedTextField(irpef, { irpef = it }, label = { Text("IRPEF %") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(scatti, { scatti = it }, label = { Text("Scatti") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text(
                    "IRPEF e scatti sono parametri salvati ma non vengono ancora applicati al totale lordo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { PresetSettingsCard() }
        item { SpecialCalendarSettingsCard() }
        item { BackupSettingsCard(repository) }

        savedMessage?.let { message ->
            item { InfoPanel(message) }
        }

        item {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val fixedCents = fixedBase.replace(',', '.').toDoubleOrNull()?.times(100)?.roundToLong() ?: return@Button
                    val hourlyCents = hourlyRate.replace(',', '.').toDoubleOrNull()?.times(100)?.roundToLong() ?: return@Button
                    val doubleCents = doubleBase.replace(',', '.').toDoubleOrNull()?.times(100)?.roundToLong() ?: return@Button
                    val irpefBp = irpef.replace(',', '.').toDoubleOrNull()?.times(100)?.roundToLong() ?: return@Button
                    val stepCount = scatti.toIntOrNull() ?: return@Button
                    scope.launch {
                        repository.saveWorker(
                            WorkerEntity(
                                id = worker?.id ?: 0,
                                name = name.ifBlank { "Lavoratore" },
                                hourlyRateCents = hourlyCents,
                                basePayMode = mode,
                                baseShiftCents = fixedCents,
                                doubleBaseCents = doubleCents,
                                irpefBasisPoints = irpefBp,
                                senioritySteps = stepCount
                            )
                        )
                    }
                    savedMessage = "Impostazioni salvate."
                }
            ) {
                Text("Salva impostazioni")
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            content()
        }
    }
}

@Composable
private fun SettingsMoneyField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("$label €") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        trailing?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun parseShiftOrNull(
    id: Long,
    workerId: Long,
    startText: String,
    endText: String,
    role: String,
    notes: String,
    performanceType: PerformanceType
): ShiftEntity? = runCatching {
    val zone = ZoneId.of("Europe/Rome")
    val start = LocalDateTime.parse(startText, editFormatter).atZone(zone).toInstant().toEpochMilli()
    val end = LocalDateTime.parse(endText, editFormatter).atZone(zone).toInstant().toEpochMilli()
    require(end > start)
    ShiftEntity(
        id = id,
        workerId = workerId,
        startEpochMillis = start,
        endEpochMillis = end,
        zoneId = zone.id,
        role = role,
        notes = notes,
        performanceType = performanceType
    )
}.getOrNull()

private fun expandShiftRange(base: ShiftEntity, endDate: LocalDate): List<ShiftEntity> {
    val zone = ZoneId.of(base.zoneId)
    val start = Instant.ofEpochMilli(base.startEpochMillis).atZone(zone)
    val end = Instant.ofEpochMilli(base.endEpochMillis).atZone(zone)
    val firstDate = start.toLocalDate()
    val lastDate = if (endDate.isBefore(firstDate)) firstDate else endDate

    val result = mutableListOf<ShiftEntity>()
    var offset = 0L
    var date = firstDate
    while (!date.isAfter(lastDate)) {
        result += base.copy(
            id = 0,
            startEpochMillis = start.plusDays(offset).toInstant().toEpochMilli(),
            endEpochMillis = end.plusDays(offset).toInstant().toEpochMilli()
        )
        offset += 1
        date = date.plusDays(1)
    }
    return result
}

private fun performanceInfo(worker: WorkerEntity, type: PerformanceType): String = when (type) {
    PerformanceType.TURNO ->
        "Base ${money(worker.baseShiftCents)}. La Polivalenza viene aggiunta automaticamente ai turni lavorati con indennità di turno."
    PerformanceType.DOPPIO ->
        "Base autonoma ${money(worker.doubleBaseCents)}. Può avere Area e Disagi come un turno normale, ma non riceve Polivalenza né Mezza IMA."
    PerformanceType.MEZZO_DOPPIO ->
        "Base ${money((worker.doubleBaseCents / 2.0).roundToLong())}. Area e Disagi restano interi; si dimezza solo l'indennità di turno del Doppio."
}

private fun categoryEditorTitle(category: AllowanceCategory, performanceType: PerformanceType): String = when (category) {
    AllowanceCategory.TURNO -> "Indennità di turno"
    AllowanceCategory.MEZZO_TURNO -> "Mezzo turno"
    AllowanceCategory.DOPPIO -> if (performanceType == PerformanceType.MEZZO_DOPPIO) "Indennità Mezzo Doppio" else "Indennità Doppio"
    AllowanceCategory.AVVIAMENTO -> "Avviamento"
    AllowanceCategory.DISAGIO -> "Disagi"
    AllowanceCategory.AREA -> "Area"
    AllowanceCategory.ALTRE_VOCI -> "Altre voci"
    AllowanceCategory.ALTRO -> "Altro"
}

private fun toggleRule(
    current: Set<Long>,
    rule: AllowanceRuleEntity,
    allRules: List<AllowanceRuleEntity>,
    checked: Boolean
): Set<Long> {
    val halfTurnCodes = setOf("DOP_TU_MEZZO", "DOP_ON_MEZZO")
    val mezzaImaId = allRules.firstOrNull {
        it.code == "ALT_MEZZA_IMA" &&
            it.enabled &&
            (it.performanceMask and PerformanceType.TURNO.maskBit) != 0
    }?.id

    if (!checked) {
        val withoutRule = current - rule.id
        return if (rule.code in halfTurnCodes && mezzaImaId != null) {
            withoutRule - mezzaImaId
        } else {
            withoutRule
        }
    }

    val group = rule.exclusiveGroup
    val withRule = if (group.isNullOrBlank()) {
        current + rule.id
    } else {
        val groupIds = allRules.filter { it.exclusiveGroup == group }.map { it.id }.toSet()
        (current - groupIds) + rule.id
    }

    // TUMezzo e ONmezzo implicano sempre Mezza IMA: l'utente non deve
    // selezionarla manualmente ogni volta.
    return if (rule.code in halfTurnCodes && mezzaImaId != null) {
        withRule + mezzaImaId
    } else {
        withRule
    }
}

private fun categoriesForPerformance(type: PerformanceType): List<AllowanceCategory> = when (type) {
    PerformanceType.TURNO -> listOf(
        AllowanceCategory.TURNO,
        AllowanceCategory.MEZZO_TURNO,
        AllowanceCategory.AVVIAMENTO,
        AllowanceCategory.DISAGIO,
        AllowanceCategory.AREA,
        AllowanceCategory.ALTRE_VOCI,
        AllowanceCategory.ALTRO
    )
    PerformanceType.DOPPIO, PerformanceType.MEZZO_DOPPIO -> listOf(
        AllowanceCategory.DOPPIO,
        AllowanceCategory.AVVIAMENTO,
        AllowanceCategory.DISAGIO,
        AllowanceCategory.AREA,
        AllowanceCategory.ALTRE_VOCI,
        AllowanceCategory.ALTRO
    )
}

private fun categoryLabel(category: AllowanceCategory): String = when (category) {
    AllowanceCategory.TURNO -> "Turni"
    AllowanceCategory.MEZZO_TURNO -> "Mezzi turni"
    AllowanceCategory.AVVIAMENTO -> "Avviamento"
    AllowanceCategory.DISAGIO -> "Disagi"
    AllowanceCategory.AREA -> "Area"
    AllowanceCategory.DOPPIO -> "Doppi"
    AllowanceCategory.ALTRE_VOCI -> "Altre voci"
    AllowanceCategory.ALTRO -> "Altro"
}

private fun categoryShortLabel(category: AllowanceCategory): String = when (category) {
    AllowanceCategory.AVVIAMENTO -> "Avviam."
    AllowanceCategory.MEZZO_TURNO -> "Mezzo turno"
    AllowanceCategory.ALTRE_VOCI -> "Altre"
    else -> categoryLabel(category)
}

private fun performanceLabel(type: PerformanceType): String = when (type) {
    PerformanceType.TURNO -> "Turno"
    PerformanceType.DOPPIO -> "Doppio"
    PerformanceType.MEZZO_DOPPIO -> "Mezzo Doppio"
}

private fun performanceColor(type: PerformanceType): Color = when (type) {
    PerformanceType.TURNO -> PortGreen
    PerformanceType.DOPPIO -> PortPurple
    PerformanceType.MEZZO_DOPPIO -> PortOrange
}

private fun typeLabel(type: AllowanceCalculationType): String = when (type) {
    AllowanceCalculationType.FIXED_PER_SHIFT -> "Fissa"
    AllowanceCalculationType.PER_HOUR -> "€/h"
    AllowanceCalculationType.PERCENT_BASE -> "%"
}

private fun ruleDescription(rule: AllowanceRuleEntity): String {
    val amount = when (rule.calculationType) {
        AllowanceCalculationType.PERCENT_BASE -> "${rule.value / 100.0}% della base"
        AllowanceCalculationType.PER_HOUR -> "${money(rule.value)}/h"
        AllowanceCalculationType.FIXED_PER_SHIFT -> money(rule.value)
    }
    val window = if (rule.windowStartMinute != null && rule.windowEndMinute != null) {
        " • ${minutesToText(rule.windowStartMinute)}-${minutesToText(rule.windowEndMinute)}"
    } else ""
    val relation = if (rule.recommendedWithAnyTagCsv?.contains("MEZZO_TURNO") == true) " • normalmente con mezzo turno" else ""
    return amount + window + relation
}

private fun ruleValueLabel(rule: AllowanceRuleEntity, performanceType: PerformanceType): String {
    if (rule.calculationType == AllowanceCalculationType.PERCENT_BASE) return "${rule.value / 100.0}%"
    if (rule.calculationType == AllowanceCalculationType.PER_HOUR) return "${money(rule.value)}/h"
    val cents = if (performanceType == PerformanceType.MEZZO_DOPPIO && rule.category == AllowanceCategory.DOPPIO) {
        (rule.value / 2.0).roundToLong()
    } else rule.value
    return money(cents)
}

private fun mainAllowanceName(row: ShiftWithPay): String? = row.selectedRules.firstOrNull {
    it.category == AllowanceCategory.TURNO ||
        it.category == AllowanceCategory.DOPPIO ||
        it.category == AllowanceCategory.MEZZO_TURNO
}?.name

private fun rowDate(row: ShiftWithPay): LocalDate = rowStart(row).toLocalDate()

private fun rowStart(row: ShiftWithPay): LocalDateTime {
    val zone = ZoneId.of(row.shift.zoneId)
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(row.shift.startEpochMillis), zone)
}

private fun rowEnd(row: ShiftWithPay): LocalDateTime {
    val zone = ZoneId.of(row.shift.zoneId)
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(row.shift.endEpochMillis), zone)
}

private fun clampDateToMonth(date: LocalDate, month: YearMonth): LocalDate =
    month.atDay(minOf(date.dayOfMonth, month.lengthOfMonth()))

private fun money(cents: Long): String = "€ %.2f".format(Locale.ITALY, cents / 100.0)

private fun Long.toEuroText(): String = "%.2f".format(Locale.US, this / 100.0)

private fun italianTitle(text: String): String = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ITALIAN) else it.toString() }

private fun parseTags(csv: String?): Set<String> = csv
    ?.split(',')
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.toSet()
    ?: emptySet()

private fun valueForEditor(rule: AllowanceRuleEntity?): String = when {
    rule == null -> "0.00"
    else -> "%.2f".format(Locale.US, rule.value / 100.0)
}

private fun minutesToText(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

private fun parseTimeMinutes(text: String): Int {
    val parts = text.trim().split(':')
    require(parts.size == 2)
    val hour = parts[0].toInt()
    val minute = parts[1].toInt()
    require(hour in 0..23 && minute in 0..59)
    return hour * 60 + minute
}
