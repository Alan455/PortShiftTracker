from pathlib import Path

ui_path = Path("app/src/main/java/it/alantamanti/portshifttracker/ui/PortShiftApp.kt")
build_path = Path("app/build.gradle.kts")
text = ui_path.read_text(encoding="utf-8")


def replace_block(source: str, start_marker: str, end_marker: str, replacement: str) -> str:
    start = source.index(start_marker)
    end = source.index(end_marker, start)
    return source[:start] + replacement.rstrip() + "\n\n" + source[end:]

editor = r'''@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShiftEditorScreen(
    worker: WorkerEntity,
    rules: List<AllowanceRuleEntity>,
    initialDate: LocalDate,
    initialShift: ShiftEntity?,
    initialSelectedIds: Set<Long>,
    onDismiss: () -> Unit,
    onSave: (ShiftEntity, Set<Long>) -> Unit
) {
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
    var error by remember { mutableStateOf<String?>(null) }
    var showNotes by remember(initialShift?.id) { mutableStateOf(initialShift?.notes?.isNotBlank() == true) }
    var showBreakdown by remember(initialShift?.id) { mutableStateOf(false) }
    val calculator = remember { AllowanceCalculator() }

    val manualRules = rules.filter {
        it.enabled &&
            it.applicationMode == AllowanceApplicationMode.MANUAL &&
            (it.performanceMask and performanceType.maskBit) != 0
    }
    val selectedRules = manualRules.filter { it.id in selectedIds }
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
        rule.id !in selectedIds && parseTags(rule.recommendedWithAnyTagCsv).any { it in selectedTags }
    }

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
            calculator.calculate(worker.toDomain(), it.toDomain(), rules.map { rule -> rule.toDomain() }, selectedIds)
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
                                    italianTitle(initialDate.format(shortDayFormatter)),
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
                                        "${selectedIds.size} indennità selezionate",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    preview?.let {
                                        Text(
                                            "Base ${money(it.basePayCents)}  •  Indennità ${money(it.allowancesCents)}",
                                            style = MaterialTheme.typography.bodySmall,
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
                                    if (shift == null) {
                                        error = "Controlla data e orari: la fine deve essere successiva all'inizio."
                                    } else {
                                        error = null
                                        onSave(shift, selectedIds)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(if (initialShift == null) "Salva prestazione" else "Salva modifiche", fontWeight = FontWeight.SemiBold)
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
                                                selectedIds = selectedIds.filterTo(mutableSetOf()) { id ->
                                                    rules.firstOrNull { it.id == id }
                                                        ?.let { (it.performanceMask and type.maskBit) != 0 } == true
                                                }
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
                                            "${selectedIds.size} indennità",
                                            style = MaterialTheme.typography.bodySmall,
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

                    item {
                        EditorSectionCard(title = "2. Data, orario e mansione") {
                            Text(
                                "Formato data e ora: AAAA-MM-GG HH:MM",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = startText,
                                onValueChange = { startText = it },
                                label = { Text("Inizio") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = endText,
                                onValueChange = { endText = it },
                                label = { Text("Fine") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Durata rapida", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(4L, 6L, 8L).forEach { hours ->
                                    OutlinedButton(
                                        onClick = {
                                            runCatching { LocalDateTime.parse(startText, editFormatter) }
                                                .getOrNull()
                                                ?.let { endText = it.plusHours(hours).format(editFormatter) }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("+$hours h")
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
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
                        SectionHeader(
                            title = "3. Indennità",
                            trailing = if (selectedIds.isEmpty()) "Nessuna" else "${selectedIds.size} selezionate"
                        )
                    }

                    categoriesForPerformance(performanceType).forEach { category ->
                        val categoryRules = manualRules.filter { it.category == category }
                        if (categoryRules.isNotEmpty()) {
                            item {
                                AllowanceCategoryCard(
                                    category = category,
                                    rules = categoryRules,
                                    selectedIds = selectedIds,
                                    performanceType = performanceType,
                                    onToggle = { rule, checked ->
                                        selectedIds = toggleRule(selectedIds, rule, manualRules, checked)
                                    }
                                )
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
                                            onClick = { selectedIds = toggleRule(selectedIds, rule, manualRules, true) }
                                        ) {
                                            Text("＋ ${rule.name}")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    relationWarnings.forEach { warning ->
                        item { WarningPanel(warning) }
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
}'''

allowance = r'''@Composable
private fun AllowanceCategoryCard(
    category: AllowanceCategory,
    rules: List<AllowanceRuleEntity>,
    selectedIds: Set<Long>,
    performanceType: PerformanceType,
    onToggle: (AllowanceRuleEntity, Boolean) -> Unit
) {
    val selectedCount = rules.count { it.id in selectedIds }
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

            rules.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { rule ->
                        val checked = rule.id in selectedIds
                        val shape = RoundedCornerShape(14.dp)
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 76.dp)
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
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}'''

text = replace_block(
    text,
    "@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun ShiftEditorScreen",
    "@Composable\nprivate fun EditorSectionCard",
    editor
)
text = replace_block(
    text,
    "@Composable\nprivate fun AllowanceCategoryCard",
    "@Composable\nprivate fun InfoPanel",
    allowance
)
ui_path.write_text(text, encoding="utf-8")

build = build_path.read_text(encoding="utf-8")
build = build.replace('versionCode = 9', 'versionCode = 10')
build = build.replace('versionName = "0.7.1"', 'versionName = "0.7.2"')
build_path.write_text(build, encoding="utf-8")

print("Applied v0.7.2 editor revision")
