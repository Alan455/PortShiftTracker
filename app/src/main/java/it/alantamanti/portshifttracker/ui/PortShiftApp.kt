package it.alantamanti.portshifttracker.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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

internal val editFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
internal val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
internal val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)
internal val dayTitleFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN)
internal val shortDayFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)

internal val PortBlue = Color(0xFF0B5FBE)
internal val PortBlueDark = Color(0xFF12477E)
internal val PortSurface = Color(0xFFF6F8FC)
internal val PortPurple = Color(0xFF7357D9)
internal val PortOrange = Color(0xFFE17932)
internal val PortGreen = Color(0xFF159A80)

// Voci ritirate: restano nel DB per non alterare eventuali storico/backup,
// ma non sono più selezionabili né mostrate nell'editor delle indennità.
internal val retiredRuleCodes = setOf("ALT_BUON_PASTO", "ALT_CRAL", "ALT_MOD_DOPPIO", "ALT_GIORNALIERO_87")

internal val portColorScheme = lightColorScheme(
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

internal val visibleCategories = listOf(
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
    HISTORY("Storico", "⌕"),
    RULES("Indennità", "€"),
    SETTINGS("Impostazioni", "⚙")
}

internal enum class RuleFilter(val label: String) {
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
                    MainTab.HISTORY -> HistoryScreen(repository)
                    MainTab.RULES -> RulesScreen(repository)
                    MainTab.SETTINGS -> SettingsScreen(repository)
                }
            }
        }
    }
}

@Composable
internal fun MonthCalendarCard(
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

            AnimatedContent(
                targetState = month,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith
                        fadeOut(animationSpec = tween(160))
                },
                label = "Cambio mese calendario"
            ) { displayedMonth ->
                val offset = displayedMonth.atDay(1).dayOfWeek.value - 1
                val count = offset + displayedMonth.lengthOfMonth()
                val displayedWeeks = (count + 6) / 7
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    (0 until displayedWeeks * 7).map { index ->
                        val day = index - offset + 1
                        if (day in 1..displayedMonth.lengthOfMonth()) displayedMonth.atDay(day) else null
                    }.chunked(7).forEach { week ->
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
    }
}

@Composable
internal fun CalendarDay(
    date: LocalDate,
    selected: Boolean,
    dayRows: List<ShiftWithPay>,
    onClick: () -> Unit
) {
    val today = date == LocalDate.now()
    val shape = RoundedCornerShape(10.dp)
    val selectionColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(180),
        label = "Bordo giorno selezionato"
    )
    val cellColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            today -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else -> Color.Transparent
        },
        animationSpec = tween(180),
        label = "Sfondo giorno selezionato"
    )
    val visibleRows = dayRows.sortedBy { it.shift.startEpochMillis }.take(2)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 2.dp)
            .border(2.dp, selectionColor, shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = cellColor
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                date.dayOfMonth.toString(),
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected || today) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )

            if (visibleRows.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 3.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    visibleRows.forEach { row ->
                        val code = calendarDisplayCode(row)
                        val accent = calendarAccentColor(code)
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(16.dp),
                            shape = RoundedCornerShape(5.dp),
                            color = accent.copy(alpha = 0.20f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    code,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = accent
                                )
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(3.dp))
            }
        }
    }
}

@Composable
internal fun EmptyDayCard() {
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
internal fun ShiftCompactCard(
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

internal fun calendarAccentColor(code: String): Color = when (code.removePrefix("½")) {
    "M" -> Color(0xFF159A80)
    "P" -> Color(0xFF3F7FE8)
    "S" -> Color(0xFFD99A00)
    "S2" -> Color(0xFF7357D9)
    "N" -> Color(0xFFD95C69)
    "Ff" -> Color(0xFFD9657A)
    "Mm" -> Color(0xFF159A80)
    "Ds" -> Color(0xFFE17932)
    "PC" -> Color(0xFF7357D9)
    "II" -> Color(0xFF607D9B)
    else -> PortBlue
}

@Composable
internal fun DayDetailsCard(
    rows: List<ShiftWithPay>,
    onDetails: (ShiftWithPay) -> Unit,
    onEdit: (ShiftWithPay) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            rows.forEachIndexed { index, row ->
                ShiftDaySection(
                    row = row,
                    onDetails = { onDetails(row) },
                    onEdit = { onEdit(row) }
                )
                if (index < rows.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Totale giornata", fontWeight = FontWeight.SemiBold)
                Text(
                    money(rows.sumOf { it.pay.totalPayCents }),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ShiftDaySection(
    row: ShiftWithPay,
    onDetails: () -> Unit,
    onEdit: () -> Unit
) {
    val code = calendarDisplayCode(row)
    val accent = calendarAccentColor(code)
    val mainRuleIds = row.selectedRules
        .filter {
            it.category == AllowanceCategory.TURNO ||
                it.category == AllowanceCategory.DOPPIO ||
                it.category == AllowanceCategory.MEZZO_TURNO
        }
        .map { it.id }
        .toSet()
    val allowanceLines = row.pay.allowanceLines.filterNot { it.ruleId in mainRuleIds }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = accent.copy(alpha = 0.16f)
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    calendarDisplayLabel(row),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accent.copy(alpha = 0.22f)
                ) {
                    Text(
                        code,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                }
            }
        }

        if (allowanceLines.isEmpty()) {
            Text(
                "Nessuna indennità aggiuntiva",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            allowanceLines.forEach { line ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(line.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (line.amountCents >= 0) "+ ${money(line.amountCents)}" else money(line.amountCents),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDetails) { Text("Dettagli") }
            TextButton(onClick = onEdit) { Text("Modifica") }
        }
    }
}

@Composable
internal fun ShiftDetailDialog(
    row: ShiftWithPay,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
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
        confirmButton = {
            Row {
                TextButton(onClick = onCopy) { Text("Copia") }
                TextButton(onClick = onEdit) { Text("Modifica") }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Chiudi") }
            }
        }
    )
}

@Composable
internal fun BreakdownLine(label: String, cents: Long, bold: Boolean = false, primary: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
        Text(
            money(cents),
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
internal fun PickerField(
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
internal fun EditorSectionCard(title: String, content: @Composable () -> Unit) {
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
internal fun AllowanceCategoryCard(
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
internal fun AllowanceRuleTile(
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
internal fun InfoPanel(text: String) {
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
internal fun WarningPanel(text: String) {
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
internal fun PerformanceTotalRow(type: PerformanceType, count: Int, totalCents: Long) {
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
internal fun EmptySummaryCard() {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Text(
            "Nessuna prestazione registrata in questo mese.",
            Modifier.padding(18.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun RuleCard(
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
internal fun RuleEditorDialog(
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
internal fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            content()
        }
    }
}

@Composable
internal fun SettingsMoneyField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("$label €") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
internal fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun SectionHeader(title: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        trailing?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun parseShiftOrNull(
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

internal fun expandShiftRange(base: ShiftEntity, endDate: LocalDate): List<ShiftEntity> {
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

internal fun performanceInfo(worker: WorkerEntity, type: PerformanceType): String = when (type) {
    PerformanceType.TURNO ->
        "Base ${money(worker.baseShiftCents)}. La Polivalenza viene aggiunta automaticamente ai turni lavorati con indennità di turno."
    PerformanceType.DOPPIO ->
        "Base autonoma ${money(worker.doubleBaseCents)}. Può avere Area e Disagi come un turno normale, ma non riceve Polivalenza né Mezza IMA."
    PerformanceType.MEZZO_DOPPIO ->
        "Base ${money((worker.doubleBaseCents / 2.0).roundToLong())}. Area e Disagi restano interi; si dimezza solo l'indennità di turno del Doppio."
}

internal fun categoryEditorTitle(category: AllowanceCategory, performanceType: PerformanceType): String = when (category) {
    AllowanceCategory.TURNO -> "Indennità di turno"
    AllowanceCategory.MEZZO_TURNO -> "Mezzo turno"
    AllowanceCategory.DOPPIO -> if (performanceType == PerformanceType.MEZZO_DOPPIO) "Indennità Mezzo Doppio" else "Indennità Doppio"
    AllowanceCategory.AVVIAMENTO -> "Avviamento"
    AllowanceCategory.DISAGIO -> "Disagi"
    AllowanceCategory.AREA -> "Area"
    AllowanceCategory.ALTRE_VOCI -> "Altre voci"
    AllowanceCategory.ALTRO -> "Altro"
}

internal fun toggleRule(
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

internal fun categoriesForPerformance(type: PerformanceType): List<AllowanceCategory> = when (type) {
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

internal fun categoryLabel(category: AllowanceCategory): String = when (category) {
    AllowanceCategory.TURNO -> "Turni"
    AllowanceCategory.MEZZO_TURNO -> "Mezzi turni"
    AllowanceCategory.AVVIAMENTO -> "Avviamento"
    AllowanceCategory.DISAGIO -> "Disagi"
    AllowanceCategory.AREA -> "Area"
    AllowanceCategory.DOPPIO -> "Doppi"
    AllowanceCategory.ALTRE_VOCI -> "Altre voci"
    AllowanceCategory.ALTRO -> "Altro"
}

internal fun categoryShortLabel(category: AllowanceCategory): String = when (category) {
    AllowanceCategory.AVVIAMENTO -> "Avviam."
    AllowanceCategory.MEZZO_TURNO -> "Mezzo turno"
    AllowanceCategory.ALTRE_VOCI -> "Altre"
    else -> categoryLabel(category)
}

internal fun performanceLabel(type: PerformanceType): String = when (type) {
    PerformanceType.TURNO -> "Turno"
    PerformanceType.DOPPIO -> "Doppio"
    PerformanceType.MEZZO_DOPPIO -> "Mezzo Doppio"
}

internal fun performanceColor(type: PerformanceType): Color = when (type) {
    PerformanceType.TURNO -> PortGreen
    PerformanceType.DOPPIO -> PortPurple
    PerformanceType.MEZZO_DOPPIO -> PortOrange
}

internal fun typeLabel(type: AllowanceCalculationType): String = when (type) {
    AllowanceCalculationType.FIXED_PER_SHIFT -> "Fissa"
    AllowanceCalculationType.PER_HOUR -> "€/h"
    AllowanceCalculationType.PERCENT_BASE -> "%"
}

internal fun ruleDescription(rule: AllowanceRuleEntity): String {
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

internal fun ruleValueLabel(rule: AllowanceRuleEntity, performanceType: PerformanceType): String {
    if (rule.calculationType == AllowanceCalculationType.PERCENT_BASE) return "${rule.value / 100.0}%"
    if (rule.calculationType == AllowanceCalculationType.PER_HOUR) return "${money(rule.value)}/h"
    val cents = if (performanceType == PerformanceType.MEZZO_DOPPIO && rule.category == AllowanceCategory.DOPPIO) {
        (rule.value / 2.0).roundToLong()
    } else rule.value
    return money(cents)
}

internal fun mainAllowanceName(row: ShiftWithPay): String? = row.selectedRules.firstOrNull {
    it.category == AllowanceCategory.TURNO ||
        it.category == AllowanceCategory.DOPPIO ||
        it.category == AllowanceCategory.MEZZO_TURNO
}?.name

internal fun rowDate(row: ShiftWithPay): LocalDate = rowStart(row).toLocalDate()

internal fun rowStart(row: ShiftWithPay): LocalDateTime {
    val zone = ZoneId.of(row.shift.zoneId)
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(row.shift.startEpochMillis), zone)
}

internal fun rowEnd(row: ShiftWithPay): LocalDateTime {
    val zone = ZoneId.of(row.shift.zoneId)
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(row.shift.endEpochMillis), zone)
}

internal fun clampDateToMonth(date: LocalDate, month: YearMonth): LocalDate =
    month.atDay(minOf(date.dayOfMonth, month.lengthOfMonth()))

internal fun money(cents: Long): String = "€ %.2f".format(Locale.ITALY, cents / 100.0)

internal fun Long.toEuroText(): String = "%.2f".format(Locale.US, this / 100.0)

internal fun italianTitle(text: String): String = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ITALIAN) else it.toString() }

internal fun parseTags(csv: String?): Set<String> = csv
    ?.split(',')
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.toSet()
    ?: emptySet()

internal fun valueForEditor(rule: AllowanceRuleEntity?): String = when {
    rule == null -> "0.00"
    else -> "%.2f".format(Locale.US, rule.value / 100.0)
}

internal fun minutesToText(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

internal fun parseTimeMinutes(text: String): Int {
    val parts = text.trim().split(':')
    require(parts.size == 2)
    val hour = parts[0].toInt()
    val minute = parts[1].toInt()
    require(hour in 0..23 && minute in 0..59)
    return hour * 60 + minute
}
