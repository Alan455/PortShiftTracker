from pathlib import Path

path = Path("app/src/main/java/it/alantamanti/portshifttracker/ui/PortShiftApp.kt")
text = path.read_text(encoding="utf-8")


def replace_block(source: str, start_marker: str, end_marker: str, replacement: str) -> str:
    start = source.index(start_marker)
    end = source.index(end_marker, start)
    return source[:start] + replacement.rstrip() + "\n\n" + source[end:]

home = r'''@Composable
private fun HomeScreen(repository: PortRepository) {
    val rows by repository.shiftRows.collectAsState(initial = emptyList())
    val workers by repository.workers.collectAsState(initial = emptyList())
    val rules by repository.rules.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

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
            onDismiss = { editorDate = null },
            onSave = { shift, selectedIds ->
                scope.launch { repository.addShiftWithSelections(shift, selectedIds) }
                editorDate = null
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
            onDismiss = { editingRow = null },
            onSave = { shift, selectedIds ->
                scope.launch { repository.updateShiftWithSelections(shift, selectedIds) }
                editingRow = null
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
}'''

calendar = r'''@Composable
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
                            modifier = Modifier.weight(1f).height(38.dp),
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
}'''

calendar_day = r'''@Composable
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
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            dayRows.take(3).forEach { row ->
                Box(
                    Modifier
                        .size(3.5.dp)
                        .clip(CircleShape)
                        .background(performanceColor(row.shift.performanceType))
                )
            }
        }
    }
}'''

empty_day = r'''@Composable
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
}'''

text = replace_block(text, "@Composable\nprivate fun HomeScreen", "@Composable\nprivate fun MonthCalendarCard", home)
text = replace_block(text, "@Composable\nprivate fun MonthCalendarCard", "@Composable\nprivate fun CalendarDay", calendar)
text = replace_block(text, "@Composable\nprivate fun CalendarDay", "@Composable\nprivate fun EmptyDayCard", calendar_day)
text = replace_block(text, "@Composable\nprivate fun EmptyDayCard", "@Composable\nprivate fun ShiftCompactCard", empty_day)

path.write_text(text, encoding="utf-8")
print("UI revision applied to", path)
