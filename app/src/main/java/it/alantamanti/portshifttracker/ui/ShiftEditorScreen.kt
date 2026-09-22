package it.alantamanti.portshifttracker.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import kotlinx.coroutines.delay
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

private enum class EditorPerformanceChoice {
    TURNO,
    GIORNALIERO,
    DOPPIO,
    MEZZO_DOPPIO
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShiftEditorScreen(
    worker: WorkerEntity,
    rules: List<AllowanceRuleEntity>,
    initialDate: LocalDate,
    initialShift: ShiftEntity?,
    initialSelectedIds: Set<Long>,
    guidedEntry: GuidedEntryKind? = null,
    isCopy: Boolean = false,
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
    val guidedInitialIds = remember(guidedEntry, rules) {
        guidedEntry?.initialRuleCodes()
            ?.mapNotNull { code -> rules.firstOrNull { it.enabled && it.code == code }?.id }
            ?.toSet()
            .orEmpty()
    }
    val guidedPerformanceType = guidedEntry?.initialPerformanceType()

    var startText by remember(initialShift?.id, initialDate) { mutableStateOf(initialStart.format(editFormatter)) }
    var endText by remember(initialShift?.id, initialDate) { mutableStateOf(initialEnd.format(editFormatter)) }
    var role by remember(initialShift?.id) { mutableStateOf(initialShift?.role ?: "Operatore") }
    var notes by remember(initialShift?.id) { mutableStateOf(initialShift?.notes.orEmpty()) }
    var performanceType by remember(initialShift?.id, guidedEntry) {
        mutableStateOf(guidedPerformanceType ?: initialShift?.performanceType ?: PerformanceType.TURNO)
    }
    var selectedIds by remember(initialShift?.id, guidedEntry, guidedInitialIds) {
        mutableStateOf(if (guidedEntry != null) guidedInitialIds else initialSelectedIds)
    }
    var rangeEndDate by remember(initialShift?.id, initialDate) { mutableStateOf(initialDate) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var showNotes by remember(initialShift?.id) { mutableStateOf(initialShift?.notes?.isNotBlank() == true) }
    var showBreakdown by remember(initialShift?.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var quickKind by remember(
        initialShift?.id,
        initialSelectedIds,
        rules,
        guidedEntry,
        guidedInitialIds,
        guidedPerformanceType
    ) {
        mutableStateOf(
            inferQuickShiftKind(
                if (guidedEntry != null) guidedInitialIds else initialSelectedIds,
                rules,
                guidedPerformanceType ?: initialShift?.performanceType ?: PerformanceType.TURNO
            )
        )
    }
    val isGiornaliero = performanceType == PerformanceType.TURNO &&
        quickKind == QuickShiftKind.GIORNALIERO
    val editorDate = runCatching { LocalDateTime.parse(startText, editFormatter).toLocalDate() }.getOrDefault(initialDate)
    val specialOverrideClass = specialDays.firstOrNull { it.epochDay == editorDate.toEpochDay() }
        ?.dayClass?.toPortDayClass()
    // Turno ordinario e Doppio usano sempre la selezione rapida basata sulla
    // data scelta nel calendario. Solo il Mezzo Doppio mantiene data/orario.
    val effectiveQuickMode =
        performanceType == PerformanceType.TURNO ||
            performanceType == PerformanceType.DOPPIO ||
            (guidedEntry == GuidedEntryKind.SECOND_MEZZO_DOPPIO &&
                performanceType == PerformanceType.MEZZO_DOPPIO)
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
    val hasVisibleManualAllowances = manualRules.any { rule ->
        guidedEntry == null || guidedRuleVisible(guidedEntry, rule)
    }
    val recentRoleOptions = remember(historyRows, editorDate) { recentRoles(historyRows, editorDate) }
    val repeatCandidate = remember(historyRows, editorDate, initialShift?.id) {
        if (initialShift == null && !isCopy) lastRepeatCandidate(historyRows, editorDate) else null
    }
    val absenceRule = selectedRules.firstOrNull { it.code == "ALT_FERIE" || it.code == "ALT_MALATTIA" }
    val rangeEnabled = initialShift == null && !isCopy && absenceRule != null
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
        (guidedEntry == null || guidedRuleVisible(guidedEntry, rule)) &&
            rule.id !in normalizedSelectedIds &&
            parseTags(rule.recommendedWithAnyTagCsv).any { it in selectedTags }
    }
    val coherenceWarnings = consistencyWarnings(performanceType, rules.filter { it.id in normalizedSelectedIds })

    val draftShift = remember(startText, endText, role, notes, performanceType, initialShift?.id) {
        parseShiftOrNull(
            id = if (isCopy) 0 else initialShift?.id ?: 0,
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
    val animatedTotal by animateFloatAsState(
        targetValue = preview?.totalPayCents?.toFloat() ?: 0f,
        animationSpec = tween(300),
        label = "Totale provvisorio"
    )
    LaunchedEffect(saved) {
        if (saved) {
            delay(420)
            onDismiss()
        }
    }

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
                                Text(
                                    when {
                                        isCopy -> "Copia prestazione"
                                        initialShift == null -> "Nuova prestazione"
                                        else -> "Modifica prestazione"
                                    }
                                )
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
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    preview?.let { money(animatedTotal.roundToLong()) } ?: "—",
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
                                                result.onSuccess { saved = true }
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
                                enabled = !saving && !saved && saveValidationMessage == null,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    when {
                                        saving -> "Salvataggio…"
                                        saved -> "✓ Prestazione salvata"
                                        isCopy -> "Salva copia"
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
                    if (guidedEntry == null) {
                        item {
                            EditorSectionCard(title = "1. Tipo di prestazione") {
                            val choices = listOf(
                                EditorPerformanceChoice.TURNO,
                                EditorPerformanceChoice.GIORNALIERO,
                                EditorPerformanceChoice.DOPPIO,
                                EditorPerformanceChoice.MEZZO_DOPPIO
                            )
                            choices.chunked(2).forEachIndexed { rowIndex, rowChoices ->
                                if (rowIndex > 0) Spacer(Modifier.height(8.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowChoices.forEach { choice ->
                                        val selected = when (choice) {
                                            EditorPerformanceChoice.TURNO ->
                                                performanceType == PerformanceType.TURNO && !isGiornaliero
                                            EditorPerformanceChoice.GIORNALIERO -> isGiornaliero
                                            EditorPerformanceChoice.DOPPIO ->
                                                performanceType == PerformanceType.DOPPIO
                                            EditorPerformanceChoice.MEZZO_DOPPIO ->
                                                performanceType == PerformanceType.MEZZO_DOPPIO
                                        }
                                        val shape = RoundedCornerShape(14.dp)
                                        val animatedBorder by animateColorAsState(
                                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            animationSpec = tween(180),
                                            label = "Bordo tipo prestazione"
                                        )
                                        val animatedBackground by animateColorAsState(
                                            if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                            animationSpec = tween(180),
                                            label = "Sfondo tipo prestazione"
                                        )
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 74.dp)
                                                .border(
                                                    width = if (selected) 2.dp else 1.dp,
                                                    color = animatedBorder,
                                                    shape = shape
                                                )
                                                .clickable {
                                                    when (choice) {
                                                        EditorPerformanceChoice.TURNO -> {
                                                            performanceType = PerformanceType.TURNO
                                                            val compatibleIds = selectedIds.filterTo(mutableSetOf()) { id ->
                                                                rules.firstOrNull { it.id == id }?.let { rule ->
                                                                    (rule.performanceMask and PerformanceType.TURNO.maskBit) != 0 &&
                                                                        rule.code != "G"
                                                                } == true
                                                            }
                                                            selectedIds = normalizeSelectedRuleIds(
                                                                PerformanceType.TURNO,
                                                                compatibleIds,
                                                                rules
                                                            )
                                                            quickKind = inferQuickShiftKind(
                                                                selectedIds,
                                                                rules,
                                                                PerformanceType.TURNO
                                                            )
                                                        }
                                                        EditorPerformanceChoice.GIORNALIERO -> {
                                                            performanceType = PerformanceType.TURNO
                                                            val compatibleIds = selectedIds.filterTo(mutableSetOf()) { id ->
                                                                rules.firstOrNull { it.id == id }?.let { rule ->
                                                                    (rule.performanceMask and PerformanceType.TURNO.maskBit) != 0 &&
                                                                        rule.code != "DOP_TU_MEZZO"
                                                                } == true
                                                            }
                                                            selectedIds = normalizeSelectedRuleIds(
                                                                PerformanceType.TURNO,
                                                                applyQuickTurnSelection(
                                                                    currentIds = compatibleIds,
                                                                    rules = rules,
                                                                    kind = QuickShiftKind.GIORNALIERO,
                                                                    date = editorDate,
                                                                    overrideClass = specialOverrideClass,
                                                                    performanceType = PerformanceType.TURNO
                                                                ),
                                                                rules
                                                            )
                                                            quickKind = QuickShiftKind.GIORNALIERO
                                                        }
                                                        EditorPerformanceChoice.DOPPIO -> {
                                                            performanceType = PerformanceType.DOPPIO
                                                            val compatibleIds = selectedIds.filterTo(mutableSetOf()) { id ->
                                                                rules.firstOrNull { it.id == id }
                                                                    ?.let { (it.performanceMask and PerformanceType.DOPPIO.maskBit) != 0 } == true
                                                            }
                                                            selectedIds = normalizeSelectedRuleIds(
                                                                PerformanceType.DOPPIO,
                                                                compatibleIds,
                                                                rules
                                                            )
                                                            quickKind = inferQuickShiftKind(
                                                                selectedIds,
                                                                rules,
                                                                PerformanceType.DOPPIO
                                                            )
                                                        }
                                                        EditorPerformanceChoice.MEZZO_DOPPIO -> {
                                                            performanceType = PerformanceType.MEZZO_DOPPIO
                                                            val compatibleIds = selectedIds.filterTo(mutableSetOf()) { id ->
                                                                rules.firstOrNull { it.id == id }
                                                                    ?.let { (it.performanceMask and PerformanceType.MEZZO_DOPPIO.maskBit) != 0 } == true
                                                            }
                                                            selectedIds = normalizeSelectedRuleIds(
                                                                PerformanceType.MEZZO_DOPPIO,
                                                                compatibleIds,
                                                                rules
                                                            )
                                                            quickKind = inferQuickShiftKind(
                                                                selectedIds,
                                                                rules,
                                                                PerformanceType.MEZZO_DOPPIO
                                                            )
                                                        }
                                                    }
                                                },
                                            shape = shape,
                                            color = animatedBackground
                                        ) {
                                            Column(
                                                Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Text(
                                                    when (choice) {
                                                        EditorPerformanceChoice.TURNO -> "T"
                                                        EditorPerformanceChoice.GIORNALIERO -> "G"
                                                        EditorPerformanceChoice.DOPPIO -> "2×"
                                                        EditorPerformanceChoice.MEZZO_DOPPIO -> "½×"
                                                    },
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    when (choice) {
                                                        EditorPerformanceChoice.TURNO -> "Turno"
                                                        EditorPerformanceChoice.GIORNALIERO -> "Giornaliero"
                                                        EditorPerformanceChoice.DOPPIO -> "Doppio"
                                                        EditorPerformanceChoice.MEZZO_DOPPIO -> "Mezzo Doppio"
                                                    },
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            InfoPanel(
                                when {
                                    isGiornaliero ->
                                        "Base fissa € 90,00. Polivalenza automatica. Con ONMezzo la base diventa € 45,00 e viene aggiunta automaticamente Mezza IMA."
                                    performanceType == PerformanceType.DOPPIO &&
                                        quickKind == QuickShiftKind.GIORNALIERO ->
                                        "Mezzo Giornaliero: base fissa € 45,00. Non riceve Mezza IMA né Polivalenza; Area, Disagi, Avviamento e Altre Voci restano disponibili."
                                    else -> performanceInfo(worker, performanceType)
                                }
                            )
                            }
                        }
                    } else {
                        item { GuidedEntrySummaryCard(guidedEntry) }
                    }

                    if (guidedEntry == null) repeatCandidate?.let { source ->
                        item {
                            val sourceKind = inferQuickShiftKind(
                                source.selectedRules.map { it.id }.toSet(),
                                rules,
                                source.shift.performanceType
                            )
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Column(
                                    Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        "Ripeti ultima configurazione",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        buildString {
                                            append(performanceLabel(source.shift.performanceType))
                                            sourceKind?.let { append(" · ${it.label}") }
                                            source.shift.role.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                                            val extras = source.selectedRules.filterNot {
                                                it.category == AllowanceCategory.TURNO ||
                                                    it.category == AllowanceCategory.DOPPIO
                                            }.take(3)
                                            if (extras.isNotEmpty()) append(" · " + extras.joinToString(" · ") { it.name })
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            performanceType = source.shift.performanceType
                                            role = source.shift.role
                                            val (ids, kind) = repeatSelectionForDate(
                                                source = source,
                                                rules = rules,
                                                targetDate = editorDate,
                                                overrideClass = specialOverrideClass
                                            )
                                            selectedIds = ids
                                            quickKind = kind
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Applica")
                                    }
                                }
                            }
                        }
                    }

                    if (
                        effectiveQuickMode &&
                        !isGiornaliero &&
                        guidedEntry?.isAbsence() != true &&
                        guidedEntry != GuidedEntryKind.SECOND_MEZZO_GIORNALIERO
                    ) {
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

                    if (guidedEntry == null) preview?.let { pay ->
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            ) {
                                Column(
                                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        "Totale provvisorio",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    PreviewBreakdownRow(
                                        label = "Base",
                                        cents = pay.basePayCents,
                                        signed = false
                                    )
                                    pay.allowanceLines
                                        .filter { it.amountCents != 0L }
                                        .forEach { line ->
                                            PreviewBreakdownRow(
                                                label = line.name,
                                                cents = line.amountCents,
                                                signed = true
                                            )
                                        }
                                }
                            }
                        }
                    }

                    if (!effectiveQuickMode && guidedEntry == null) {
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

                    if (guidedEntry == null) item {
                        EditorSectionCard(title = "Mansione e note") {
                            if (recentRoleOptions.isNotEmpty()) {
                                Text(
                                    "Mansioni recenti",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    recentRoleOptions.forEach { recentRole ->
                                        FilterChip(
                                            selected = role.equals(recentRole, ignoreCase = true),
                                            onClick = { role = recentRole },
                                            label = { Text(recentRole) }
                                        )
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
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
                            }
                            AnimatedVisibility(
                                visible = showNotes,
                                enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                                exit = fadeOut(tween(120)) + shrinkVertically(tween(180))
                            ) {
                                Column {
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
                    }

                    if (guidedEntry == null) item {
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

                    if (guidedEntry == null || hasVisibleManualAllowances) {
                        item {
                            SectionHeader(
                                title = if (guidedEntry == null) {
                                    if (effectiveQuickMode) "2. Indennità" else "3. Indennità"
                                } else {
                                    "Indennità"
                                },
                                trailing = selectedSummary
                            )
                        }
                    }

                    if (guidedEntry == null || hasVisibleManualAllowances) {
                    categoriesForPerformance(performanceType)
                        .filterNot { category ->
                            effectiveQuickMode && (
                                category == AllowanceCategory.TURNO ||
                                    category == AllowanceCategory.DOPPIO
                                )
                        }
                        .forEach { category ->
                        val categoryRules = manualRules
                            .filter { it.category == category }
                            .filter { rule -> guidedEntry == null || guidedRuleVisible(guidedEntry, rule) }
                            .filterNot { rule ->
                                isGiornaliero &&
                                    category == AllowanceCategory.MEZZO_TURNO &&
                                    rule.code != "DOP_ON_MEZZO"
                            }
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

                    if (guidedEntry != null) {
                        item {
                            EditorSectionCard(title = "Note") {
                                if (!showNotes) {
                                    TextButton(onClick = { showNotes = true }) { Text("＋ Aggiungi note") }
                                }
                                AnimatedVisibility(
                                    visible = showNotes,
                                    enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                                    exit = fadeOut(tween(120)) + shrinkVertically(tween(180))
                                ) {
                                    Column {
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
                        }
                    }

                    if (preview != null && guidedEntry == null) {
                        item {
                            TextButton(
                                onClick = { showBreakdown = !showBreakdown },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (showBreakdown) "Nascondi dettaglio calcolo" else "Vedi dettaglio calcolo")
                            }
                        }
                    }

                    if (preview != null && guidedEntry == null) {
                        item {
                            AnimatedVisibility(
                                visible = showBreakdown,
                                enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                                exit = fadeOut(tween(120)) + shrinkVertically(tween(180))
                            ) {
                                EditorSectionCard(title = "Dettaglio calcolo") {
                                    BreakdownLine("Base", preview.basePayCents)
                                    preview.allowanceLines.forEach { line ->
                                        BreakdownLine(line.name, line.amountCents)
                                    }
                                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                    BreakdownLine("Totale", preview.totalPayCents, bold = true, primary = true)
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
private fun GuidedEntrySummaryCard(entry: GuidedEntryKind) {
    val (code, title, subtitle) = when (entry) {
        GuidedEntryKind.FIRST_TURNO -> Triple("T", "Turno", "Scegli M, P, S, S2 o N")
        GuidedEntryKind.FIRST_GIORNALIERO -> Triple("G", "Giornaliero", "Base € 90,00 · ONMezzo disponibile")
        GuidedEntryKind.ABS_FERIE -> Triple("Ff", "Ferie", "Assenza")
        GuidedEntryKind.ABS_MALATTIA -> Triple("Mm", "Malattia", "Assenza")
        GuidedEntryKind.ABS_CONGEDO -> Triple("PC", "Congedo", "Assenza")
        GuidedEntryKind.ABS_IMA -> Triple("I", "IMA", "Puoi scegliere Disdetta casa o festiva")
        GuidedEntryKind.SECOND_DOPPIO -> Triple("2×", "Doppio completo", "Base e indennità turno intere")
        GuidedEntryKind.SECOND_MEZZO_DOPPIO -> Triple("½×", "Mezzo Doppio", "Base metà · indennità turno al 50%")
        GuidedEntryKind.SECOND_MEZZO_GIORNALIERO -> Triple("½G", "Mezzo Giornaliero", "Base € 45,00 · nessuna Mezza IMA")
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
    ) {
        Row(
            Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            ) {
                Text(
                    code,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private val guidedAbsenceRuleCodes = setOf(
    "ALT_FERIE", "ALT_MALATTIA", "ALT_IMA", "AVV_DS", "AVV_INAIL", "AVV_CONGEDO"
)

private fun guidedRuleVisible(entry: GuidedEntryKind, rule: AllowanceRuleEntity): Boolean = when (entry) {
    GuidedEntryKind.FIRST_TURNO ->
        rule.code !in guidedAbsenceRuleCodes &&
            rule.code != "DOP_ON_MEZZO"

    GuidedEntryKind.FIRST_GIORNALIERO ->
        rule.code !in guidedAbsenceRuleCodes &&
            rule.code != "DOP_TU_MEZZO"

    GuidedEntryKind.ABS_IMA ->
        rule.code == "AVV_DIS_CASA" || rule.code == "AVV_DIS_CASA_FEST"

    GuidedEntryKind.ABS_FERIE,
    GuidedEntryKind.ABS_MALATTIA,
    GuidedEntryKind.ABS_CONGEDO -> false

    GuidedEntryKind.SECOND_DOPPIO,
    GuidedEntryKind.SECOND_MEZZO_DOPPIO,
    GuidedEntryKind.SECOND_MEZZO_GIORNALIERO ->
        rule.code !in guidedAbsenceRuleCodes &&
            rule.code !in setOf("ALT_MEZZA_IMA", "ALT_POLIVALENZA", "DOP_TU_MEZZO", "DOP_ON_MEZZO")
}

@Composable
private fun PreviewBreakdownRow(
    label: String,
    cents: Long,
    signed: Boolean
) {
    val amount = when {
        !signed -> money(cents)
        cents >= 0L -> "+ ${money(cents)}"
        else -> "− ${money(-cents)}"
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            amount,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
