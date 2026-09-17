from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel, text):
    (ROOT / rel).write_text(text, encoding="utf-8")


def replace_once(rel, old, new):
    text = read(rel)
    if old not in text:
        raise SystemExit(f"Missing patch target in {rel}: {old[:120]!r}")
    text = text.replace(old, new, 1)
    write(rel, text)


def replace_between(rel, start_marker, end_marker, replacement):
    text = read(rel)
    start = text.find(start_marker)
    if start < 0:
        raise SystemExit(f"Missing start marker in {rel}: {start_marker!r}")
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit(f"Missing end marker in {rel}: {end_marker!r}")
    text = text[:start] + replacement + text[end:]
    write(rel, text)


# Version
replace_once(
    "app/build.gradle.kts",
    '        versionCode = 13\n        versionName = "0.9.0"',
    '        versionCode = 14\n        versionName = "0.10.0"',
)

# Quick shift mode: Giornaliero joins the five quick shifts.
replace_once(
    "app/src/main/java/it/alantamanti/portshifttracker/ui/QuickShiftMode.kt",
    '''internal enum class QuickShiftKind(val label: String) {
    MATTINA("Mattina"),
    POMERIGGIO("Pomeriggio"),
    SERA("Sera"),
    SERA2("Sera2"),
    NOTTE("Notte")
}''',
    '''internal enum class QuickShiftKind(val label: String) {
    MATTINA("Mattina"),
    POMERIGGIO("Pomeriggio"),
    SERA("Sera"),
    SERA2("Sera2"),
    NOTTE("Notte"),
    GIORNALIERO("Giornaliero")
}''',
)
replace_once(
    "app/src/main/java/it/alantamanti/portshifttracker/ui/QuickShiftMode.kt",
    '''        QuickShiftKind.NOTTE -> if (festiveForAllowance) "NOTTEF" to "NotteF" else "NOTTE" to "Notte"
    }''',
    '''        QuickShiftKind.NOTTE -> if (festiveForAllowance) "NOTTEF" to "NotteF" else "NOTTE" to "Notte"
        QuickShiftKind.GIORNALIERO -> "G" to "G"
    }''',
)
replace_once(
    "app/src/main/java/it/alantamanti/portshifttracker/ui/QuickShiftMode.kt",
    '''        "NOTTE", "NOTTEF" -> QuickShiftKind.NOTTE
        else -> null''',
    '''        "NOTTE", "NOTTEF" -> QuickShiftKind.NOTTE
        "G" -> QuickShiftKind.GIORNALIERO
        else -> null''',
)

# Quick-mode selector becomes a toggle; six quick buttons render as 3 + 3.
qpanel = "app/src/main/java/it/alantamanti/portshifttracker/ui/QuickShiftPanel.kt"
replace_once(qpanel, "import androidx.compose.material3.FilterChip\n", "import androidx.compose.material3.Switch\n")
replace_between(
    qpanel,
    "@Composable\ninternal fun EntryModeSelector(",
    "\n@Composable\ninternal fun QuickShiftPanel(",
    '''@Composable
internal fun EntryModeSelector(
    quickMode: Boolean,
    quickEnabled: Boolean,
    onQuick: () -> Unit,
    onDetailed: () -> Unit
) {
    val quickSelected = quickMode && quickEnabled
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Modalità inserimento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Dettagliato",
                    fontWeight = if (!quickSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (!quickSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = quickSelected,
                    enabled = quickEnabled,
                    onCheckedChange = { checked -> if (checked) onQuick() else onDetailed() },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Text(
                    "5 turni",
                    fontWeight = if (quickSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (quickSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                if (quickEnabled)
                    "Con 5 turni scegli il turno con un tocco: la data è quella selezionata nel calendario e data/orari non vengono mostrati."
                else
                    "La modalità 5 turni è disponibile per il Turno normale. Doppio e Mezzo Doppio restano in modalità dettagliata.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
''',
)
replace_once(
    qpanel,
    '''            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                kinds.drop(3).forEach { kind ->
                    QuickShiftTile(
                        kind = kind,
                        date = date,
                        rules = rules,
                        selected = selectedKind == kind,
                        overrideClass = overrideClass,
                        modifier = Modifier.weight(1f),
                        onClick = { onKindSelected(kind) }
                    )
                }
                Spacer(Modifier.weight(1f))
            }''',
    '''            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val remainingKinds = kinds.drop(3)
                remainingKinds.forEach { kind ->
                    QuickShiftTile(
                        kind = kind,
                        date = date,
                        rules = rules,
                        selected = selectedKind == kind,
                        overrideClass = overrideClass,
                        modifier = Modifier.weight(1f),
                        onClick = { onKindSelected(kind) }
                    )
                }
                repeat((3 - remainingKinds.size).coerceAtLeast(0)) {
                    Spacer(Modifier.weight(1f))
                }
            }''',
)

# Catalog: TUMezzo/ONmezzo replace the normal base and halve only the turn allowance.
# Retired voices are removed for fresh installations.
catalog = "app/src/main/java/it/alantamanti/portshifttracker/data/local/DefaultCatalog.kt"
replace_once(
    catalog,
    '        add(fixed("DOP_TU_MEZZO", "TUMezzo", 3390, AllowanceCategory.MEZZO_TURNO, 410, group = "MEZZO_TURNO", tagsCsv = "MEZZO_TURNO", performanceMask = PerformanceType.TURNO.maskBit))',
    '''        add(fixed(
            "DOP_TU_MEZZO", "TUMezzo", 3390, AllowanceCategory.MEZZO_TURNO, 410,
            group = "MEZZO_TURNO",
            basePayEffect = BasePayEffect.REPLACE_BASE,
            turnAllowanceMultiplierBasisPoints = 5000,
            tagsCsv = "MEZZO_TURNO",
            performanceMask = PerformanceType.TURNO.maskBit
        ))''',
)
replace_once(
    catalog,
    '        add(fixed("DOP_ON_MEZZO", "ONmezzo", 4350, AllowanceCategory.MEZZO_TURNO, 411, group = "MEZZO_TURNO", tagsCsv = "MEZZO_TURNO", performanceMask = PerformanceType.TURNO.maskBit))',
    '''        add(fixed(
            "DOP_ON_MEZZO", "ONmezzo", 4350, AllowanceCategory.MEZZO_TURNO, 411,
            group = "MEZZO_TURNO",
            basePayEffect = BasePayEffect.REPLACE_BASE,
            turnAllowanceMultiplierBasisPoints = 5000,
            tagsCsv = "MEZZO_TURNO",
            performanceMask = PerformanceType.TURNO.maskBit
        ))''',
)
for retired_line in [
    '        add(fixed("ALT_BUON_PASTO", "BuonPasto", 500, AllowanceCategory.ALTRE_VOCI, 500))\n',
    '        add(fixed("ALT_CRAL", "CRAL", 400, AllowanceCategory.ALTRE_VOCI, 501))\n',
    '        add(fixed("ALT_MOD_DOPPIO", "Mod.Doppio", 2000, AllowanceCategory.ALTRE_VOCI, 508))\n',
]:
    replace_once(catalog, retired_line, "")

# Existing databases keep personalized values but receive the corrected half-turn behavior.
app_file = "app/src/main/java/it/alantamanti/portshifttracker/PortShiftApplication.kt"
replace_once(
    app_file,
    '''        // Aggiunge soltanto le voci mancanti. Le modifiche dell'utente restano intatte.
        DefaultCatalog.rules().forEach { db.allowanceRuleDao().insertIfMissing(it) }
    }''',
    '''        // Aggiunge soltanto le voci mancanti. Le modifiche dell'utente restano intatte.
        DefaultCatalog.rules().forEach { db.allowanceRuleDao().insertIfMissing(it) }

        // TUMezzo/ONmezzo sostituiscono il turno intero: manteniamo l'importo
        // eventualmente personalizzato, ma correggiamo il comportamento anche sui DB esistenti.
        db.openHelper.writableDatabase.execSQL(
            "UPDATE allowance_rules SET basePayEffect = 'REPLACE_BASE', " +
                "turnAllowanceMultiplierBasisPoints = 5000 " +
                "WHERE code IN ('DOP_TU_MEZZO','DOP_ON_MEZZO')"
        )
    }''',
)

# Main UI/editor changes.
ui = "app/src/main/java/it/alantamanti/portshifttracker/ui/PortShiftApp.kt"
replace_once(
    ui,
    '''private val PortGreen = Color(0xFF159A80)

private val portColorScheme''',
    '''private val PortGreen = Color(0xFF159A80)

// Voci ritirate: restano nel DB per non alterare eventuali storico/backup,
// ma non sono più selezionabili né mostrate nell'editor delle indennità.
private val retiredRuleCodes = setOf("ALT_BUON_PASTO", "ALT_CRAL", "ALT_MOD_DOPPIO")

private val portColorScheme''',
)
replace_once(
    ui,
    '''    val monthRows = remember(rows, month) { rows.filter { YearMonth.from(rowDate(it)) == month } }
    val monthTotal = monthRows.sumOf { it.pay.totalPayCents }
''',
    '''    val monthRows = remember(rows, month) { rows.filter { YearMonth.from(rowDate(it)) == month } }
    val monthTotal = monthRows.sumOf { it.pay.totalPayCents }
    val ruleUsageCounts = remember(rows) {
        rows.flatMap { it.selectedRules }.groupingBy { it.id }.eachCount()
    }
''',
)
replace_once(
    ui,
    '''            initialShift = null,
            initialSelectedIds = emptySet(),
            onDismiss = { editorDate = null },
            onSave = { shift, selectedIds ->
                scope.launch { repository.addShiftWithSelections(shift, selectedIds) }
                editorDate = null
            }''',
    '''            initialShift = null,
            initialSelectedIds = emptySet(),
            ruleUsageCounts = ruleUsageCounts,
            onDismiss = { editorDate = null },
            onSave = { shifts, selectedIds ->
                scope.launch {
                    shifts.forEach { shift -> repository.addShiftWithSelections(shift, selectedIds) }
                }
                editorDate = null
            }''',
)
replace_once(
    ui,
    '''            initialShift = row.shift,
            initialSelectedIds = row.selectedRules.map { it.id }.toSet(),
            onDismiss = { editingRow = null },
            onSave = { shift, selectedIds ->
                scope.launch { repository.updateShiftWithSelections(shift, selectedIds) }
                editingRow = null
            }''',
    '''            initialShift = row.shift,
            initialSelectedIds = row.selectedRules.map { it.id }.toSet(),
            ruleUsageCounts = ruleUsageCounts,
            onDismiss = { editingRow = null },
            onSave = { shifts, selectedIds ->
                shifts.firstOrNull()?.let { shift ->
                    scope.launch { repository.updateShiftWithSelections(shift, selectedIds) }
                }
                editingRow = null
            }''',
)
replace_once(
    ui,
    '''    initialShift: ShiftEntity?,
    initialSelectedIds: Set<Long>,
    onDismiss: () -> Unit,
    onSave: (ShiftEntity, Set<Long>) -> Unit
) {''',
    '''    initialShift: ShiftEntity?,
    initialSelectedIds: Set<Long>,
    ruleUsageCounts: Map<Long, Int>,
    onDismiss: () -> Unit,
    onSave: (List<ShiftEntity>, Set<Long>) -> Unit
) {''',
)
replace_once(
    ui,
    '''    var performanceType by remember(initialShift?.id) { mutableStateOf(initialShift?.performanceType ?: PerformanceType.TURNO) }
    var selectedIds by remember(initialShift?.id) { mutableStateOf(initialSelectedIds) }
    var error by remember { mutableStateOf<String?>(null) }''',
    '''    var performanceType by remember(initialShift?.id) { mutableStateOf(initialShift?.performanceType ?: PerformanceType.TURNO) }
    var selectedIds by remember(initialShift?.id) { mutableStateOf(initialSelectedIds) }
    var rangeEndDate by remember(initialShift?.id, initialDate) { mutableStateOf(initialDate) }
    var error by remember { mutableStateOf<String?>(null) }''',
)
replace_once(
    ui,
    '''    val manualRules = rules.filter {
        it.enabled &&
            it.applicationMode == AllowanceApplicationMode.MANUAL &&''',
    '''    val manualRules = rules.filter {
        it.enabled &&
            it.code !in retiredRuleCodes &&
            it.applicationMode == AllowanceApplicationMode.MANUAL &&''',
)
replace_once(
    ui,
    '''    val selectedRules = manualRules.filter { it.id in selectedIds }
    val selectedTags = selectedRules.flatMap { parseTags(it.tagsCsv) }.toSet()''',
    '''    val selectedRules = manualRules.filter { it.id in selectedIds }
    val absenceRule = selectedRules.firstOrNull { it.code == "ALT_FERIE" || it.code == "ALT_MALATTIA" }
    val rangeEnabled = initialShift == null && absenceRule != null
    val effectiveRangeEnd = if (rangeEndDate.isBefore(editorDate)) editorDate else rangeEndDate
    val selectedTags = selectedRules.flatMap { parseTags(it.tagsCsv) }.toSet()''',
)
replace_once(
    ui,
    '''                                    } else {
                                        error = null
                                        onSave(shift, selectedIds)
                                    }''',
    '''                                    } else {
                                        error = null
                                        val shiftsToSave = if (rangeEnabled) {
                                            expandShiftRange(shift, effectiveRangeEnd)
                                        } else {
                                            listOf(shift)
                                        }
                                        onSave(shiftsToSave, selectedIds)
                                    }''',
)
replace_once(
    ui,
    '''                                Text(if (initialShift == null) "Salva prestazione" else "Salva modifiche", fontWeight = FontWeight.SemiBold)''',
    '''                                Text(
                                    when {
                                        initialShift != null -> "Salva modifiche"
                                        rangeEnabled -> "Salva periodo ${absenceRule?.name.orEmpty()}"
                                        else -> "Salva prestazione"
                                    },
                                    fontWeight = FontWeight.SemiBold
                                )''',
)
# Hide section 2 (date/time) while quick mode is active.
replace_once(
    ui,
    '''                    item {
                        val currentStart = runCatching { LocalDateTime.parse(startText, editFormatter) }.getOrDefault(initialStart)
                        val currentEnd = runCatching { LocalDateTime.parse(endText, editFormatter) }.getOrDefault(initialEnd)
''',
    '''                    if (!effectiveQuickMode) {
                        item {
                            val currentStart = runCatching { LocalDateTime.parse(startText, editFormatter) }.getOrDefault(initialStart)
                            val currentEnd = runCatching { LocalDateTime.parse(endText, editFormatter) }.getOrDefault(initialEnd)
''',
)
replace_once(
    ui,
    '''                        }
                    }

                    item {
                        EditorSectionCard(title = "Mansione e note") {''',
    '''                        }
                    }
                    }

                    item {
                        EditorSectionCard(title = "Mansione e note") {''',
)
replace_once(
    ui,
    '''                            title = "3. Indennità",
                            trailing = if (selectedIds.isEmpty()) "Nessuna" else "${selectedIds.size} selezionate"''',
    '''                            title = if (effectiveQuickMode) "2. Indennità" else "3. Indennità",
                            trailing = if (selectedIds.isEmpty()) "Nessuna" else "${selectedIds.size} selezionate"''',
)
replace_once(
    ui,
    '''                                    selectedIds = selectedIds,
                                    performanceType = performanceType,
                                    onToggle = { rule, checked ->''',
    '''                                    selectedIds = selectedIds,
                                    performanceType = performanceType,
                                    usageCounts = ruleUsageCounts,
                                    onToggle = { rule, checked ->''',
)
# Range selector appears only for a newly-created Ferie/Malattia entry.
replace_once(
    ui,
    '''                    if (suggestedCompanions.isNotEmpty()) {
                        item {''',
    '''                    if (initialShift == null) {
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
                        item {''',
)
# Rules screen also hides retired voices while keeping historical DB records intact.
replace_once(
    ui,
    '''    val filteredRules = rules.filter {
        it.category in categories &&
            (search.isBlank() || it.name.contains(search, ignoreCase = true) || it.code.contains(search, ignoreCase = true))
    }''',
    '''    val filteredRules = rules.filter {
        it.code !in retiredRuleCodes &&
            it.category in categories &&
            (search.isBlank() || it.name.contains(search, ignoreCase = true) || it.code.contains(search, ignoreCase = true))
    }''',
)

# Replace allowance category UI with frequency-sorted horizontal LazyRows for Avviamento/Disagi/Area.
replace_between(
    ui,
    "@Composable\nprivate fun AllowanceCategoryCard(",
    "\n@Composable\nprivate fun InfoPanel(",
    '''@Composable
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
''',
)

# Date-range expansion helper for Ferie/Malattia.
replace_once(
    ui,
    '''}.getOrNull()

private fun performanceInfo(worker: WorkerEntity, type: PerformanceType): String = when (type) {''',
    '''}.getOrNull()

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

private fun performanceInfo(worker: WorkerEntity, type: PerformanceType): String = when (type) {''',
)

# Tests: Giornaliero quick mode and exact TUMezzo example (33.90 + half of Sera2 13.26).
quick_test = "app/src/test/java/it/alantamanti/portshifttracker/ui/QuickShiftModeTest.kt"
replace_once(
    quick_test,
    '''    @Test
    fun ravenna_patron_and_port_november_fourth_are_festive() {''',
    '''    @Test
    fun giornaliero_is_available_with_the_other_quick_shifts() {
        assertEquals("G", resolveQuickShift(QuickShiftKind.GIORNALIERO, LocalDate.of(2026, 9, 18)).ruleCode)
        assertEquals("G", resolveQuickShift(QuickShiftKind.GIORNALIERO, LocalDate.of(2026, 9, 20)).ruleCode)
    }

    @Test
    fun ravenna_patron_and_port_november_fourth_are_festive() {''',
)

calc_test = "app/src/test/java/it/alantamanti/portshifttracker/domain/AllowanceCalculatorTest.kt"
replace_once(
    calc_test,
    '''    private val rules = listOf(turnoNotte, doppioSera, polivalenza, area, disagio, mezzaIma)
''',
    '''    private val sera2 = AllowanceRule(
        id = 7,
        name = "Sera2",
        code = "SERA2",
        calculationType = AllowanceCalculationType.FIXED_PER_SHIFT,
        value = 1326,
        category = AllowanceCategory.TURNO,
        applicationMode = AllowanceApplicationMode.MANUAL,
        performanceMask = PerformanceType.TURNO.maskBit
    )
    private val tuMezzo = AllowanceRule(
        id = 8,
        name = "TUMezzo",
        code = "DOP_TU_MEZZO",
        calculationType = AllowanceCalculationType.FIXED_PER_SHIFT,
        value = 3390,
        category = AllowanceCategory.MEZZO_TURNO,
        applicationMode = AllowanceApplicationMode.MANUAL,
        basePayEffect = BasePayEffect.REPLACE_BASE,
        turnAllowanceMultiplierBasisPoints = 5000,
        performanceMask = PerformanceType.TURNO.maskBit
    )

    private val rules = listOf(turnoNotte, doppioSera, polivalenza, area, disagio, mezzaIma)
''',
)
replace_once(
    calc_test,
    '''    @Test
    fun doppioUsaBase8840ConAreaDisagioMaSenzaPolivalenzaOMezzaIma() {''',
    '''    @Test
    fun tuMezzoSostituisceBaseEDimezzaSoloIndennitaTurno() {
        val pay = calculator.calculate(
            worker,
            shift(PerformanceType.TURNO, "2026-09-17T18:00:00", "2026-09-17T21:00:00"),
            listOf(sera2, tuMezzo),
            setOf(7, 8)
        )

        assertEquals(3390, pay.basePayCents)
        assertTrue(pay.allowanceLines.any { it.name == "Sera2" && it.amountCents == 663L })
        assertEquals(4053, pay.totalPayCents)
    }

    @Test
    fun doppioUsaBase8840ConAreaDisagioMaSenzaPolivalenzaOMezzaIma() {''',
)

print("v0.10 feature patch applied successfully")
