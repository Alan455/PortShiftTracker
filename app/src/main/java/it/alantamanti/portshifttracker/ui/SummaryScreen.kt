package it.alantamanti.portshifttracker.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity
import it.alantamanti.portshifttracker.data.local.AppFeatureStore
import it.alantamanti.portshifttracker.data.local.PayslipComparison
import it.alantamanti.portshifttracker.data.repository.PortRepository
import it.alantamanti.portshifttracker.data.repository.ShiftWithPay
import it.alantamanti.portshifttracker.domain.AllowanceCategory
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

@Composable
internal fun SummaryScreen(repository: PortRepository) {
    val rules by repository.rules.collectAsState(initial = emptyList())
    val workers by repository.workers.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val featureStore = remember(context) { AppFeatureStore(context) }
    val payslips by featureStore.payslipsFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val feedback = LocalShiftFeedback.current

    var month by remember { mutableStateOf(YearMonth.now()) }
    var showDetails by remember { mutableStateOf(false) }
    var showComparison by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }

    val zone = remember { ZoneId.of("Europe/Rome") }
    val monthStart = remember(month) { month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    val monthEnd = remember(month) { month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    val monthRowsFlow = remember(monthStart, monthEnd) {
        repository.shiftRowsBetween(monthStart, monthEnd)
    }
    val monthRows by monthRowsFlow.collectAsState(initial = emptyList())

    val previousMonth = remember(month) { month.minusMonths(1) }
    val previousStart = remember(previousMonth) {
        previousMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val previousEnd = remember(month) {
        month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val previousRowsFlow = remember(previousStart, previousEnd) {
        repository.shiftRowsBetween(previousStart, previousEnd)
    }
    val previousRows by previousRowsFlow.collectAsState(initial = emptyList())

    val total = monthRows.sumOf { it.pay.totalPayCents }
    val irpefBasisPoints = workers.firstOrNull()?.irpefBasisPoints ?: 3000L
    val previousTotal = previousRows.sumOf { it.pay.totalPayCents }
    val daysWorked = monthRows.map(::rowDate).distinct().size
    val previousDaysWorked = previousRows.map(::rowDate).distinct().size
    val averagePerDay = if (daysWorked == 0) 0L else total / daysWorked
    val previousAverage = if (previousDaysWorked == 0) 0L else previousTotal / previousDaysWorked
    val groupedDays = monthRows.groupBy(::rowDate).toList().sortedByDescending { it.first }
    val totals = remember(monthRows, rules) { monthlyCategoryTotals(monthRows, rules) }
    val categoryDetails = remember(monthRows, rules, totals) {
        summaryCategoryDetails(monthRows, rules, totals)
    }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(month) { selectedCategoryId = null }
    val existingPayslip = payslips.firstOrNull { it.month == month.toString() }
    val isLocked = existingPayslip?.locked == true

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 14.dp,
            top = 12.dp,
            end = 14.dp,
            bottom = 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SummaryPeriodHeader(
                month = month,
                onPrevious = { month = month.minusMonths(1) },
                onNext = { month = month.plusMonths(1) }
            )
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryKpiCard(
                    title = "Totale mese",
                    value = money(total),
                    delta = summaryDelta(total, previousTotal),
                    modifier = Modifier.weight(1f)
                )
                SummaryKpiCard(
                    title = "Prestazioni",
                    value = monthRows.size.toString(),
                    delta = summaryDelta(monthRows.size.toLong(), previousRows.size.toLong()),
                    modifier = Modifier.weight(1f)
                )
                SummaryKpiCard(
                    title = "Media/giorno",
                    value = money(averagePerDay),
                    delta = summaryDelta(averagePerDay, previousAverage),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            SummaryHeroTotal(
                month = month,
                total = total,
                previousTotal = previousTotal
            )
        }

        item(key = "monthly_net") {
            val selectedMonth = month
            SummaryNetCard(
                month = selectedMonth,
                grossCents = total,
                irpefBasisPoints = irpefBasisPoints,
                manuallyEnteredCents = existingPayslip?.manualNetCents,
                canUpdatePercentage = workers.firstOrNull() != null,
                onSaveManualNet = { manualCents, recalibratedBasisPoints ->
                    runCatching {
                        // Preserve all the comparison fields and the locked flag.
                        val current = featureStore.payslip(selectedMonth)
                            ?: PayslipComparison(month = selectedMonth.toString())
                        featureStore.savePayslip(current.copy(manualNetCents = manualCents))
                        if (recalibratedBasisPoints != null) {
                            workers.firstOrNull()?.let { worker ->
                                repository.saveWorker(worker.copy(irpefBasisPoints = recalibratedBasisPoints))
                            }
                        }
                    }.also { result ->
                        // Showing a snackbar can suspend until it is dismissed:
                        // do not keep the save button busy after persistence finishes.
                        scope.launch {
                            feedback.showSnackbar(
                                if (result.isFailure) "Salvataggio del netto non riuscito. Riprova."
                                else if (manualCents == null) "Netto manuale rimosso."
                                else "Netto salvato e percentuale stimata aggiornata.",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }.isSuccess
                }
            )
        }

        item {
            SummaryBreakdownCard(
                total = totals.total,
                categories = categoryDetails,
                onCategoryClick = { selectedCategoryId = it.id }
            )
        }

        item {
            SummaryMonthTrendCard(
                month = month,
                monthRows = monthRows
            )
        }

        item {
            SummaryActionsCard(
                showDetails = showDetails,
                showComparison = showComparison,
                showExport = showExport,
                locked = isLocked,
                onDetails = { showDetails = !showDetails },
                onCompare = { showComparison = !showComparison },
                onExport = { showExport = !showExport },
                onToggleLock = {
                    scope.launch {
                        val current = existingPayslip ?: PayslipComparison(month = month.toString())
                        featureStore.savePayslip(current.copy(locked = !isLocked))
                    }
                }
            )
        }

        item(key = "payslip_comparison") {
            AnimatedVisibility(
                visible = showComparison,
                enter = fadeIn(tween(190)) + expandVertically(tween(240)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(190))
            ) {
                PayslipComparisonCard(month = month, rows = monthRows, rules = rules)
            }
        }

        item(key = "monthly_export") {
            AnimatedVisibility(
                visible = showExport,
                enter = fadeIn(tween(190)) + expandVertically(tween(240)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(190))
            ) {
                MonthlyExportCard(month = month, rows = monthRows)
            }
        }

        if (showDetails) {
            item(key = "summary_days_heading") {
                SectionHeader("Giornate del mese")
            }
            if (groupedDays.isEmpty()) {
                item(key = "summary_days_empty") { EmptySummaryCard() }
            } else {
                items(groupedDays, key = { it.first }) { (date, dayRows) ->
                    SummaryDayCard(
                        date = date,
                        dayRows = dayRows,
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(180),
                            placementSpec = tween(220),
                            fadeOutSpec = tween(120)
                        )
                    )
                }
            }
        }
    }

    categoryDetails.firstOrNull { it.id == selectedCategoryId }?.let { selected ->
        SummaryCategorySheet(
            detail = selected,
            color = summaryCategoryColor(selected.id),
            onDismiss = { selectedCategoryId = null }
        )
    }
}

@Composable
private fun SummaryPeriodHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Riepilogo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        TextButton(onClick = onPrevious) { Text("‹") }
                        AnimatedContent(
                            targetState = month,
                            transitionSpec = {
                                fadeIn(tween(200)) togetherWith fadeOut(tween(140))
                            },
                            label = "Periodo riepilogo"
                        ) { displayedMonth ->
                            Text(
                                italianTitle(displayedMonth.format(monthFormatter)),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        TextButton(onClick = onNext) { Text("›") }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text("Mese") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = false,
                    onClick = {},
                    enabled = false,
                    label = { Text("Anno") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = false,
                    onClick = {},
                    enabled = false,
                    label = { Text("Custom") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryKpiCard(
    title: String,
    value: String,
    delta: SummaryDelta,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(130))
                },
                label = "Valore indicatore riepilogo"
            ) { displayedValue ->
                Text(
                    displayedValue,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                delta.label,
                style = MaterialTheme.typography.labelSmall,
                color = delta.color,
                textAlign = TextAlign.Center
            )
            Text(
                "vs. mese prec.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SummaryHeroTotal(
    month: YearMonth,
    total: Long,
    previousTotal: Long
) {
    val delta = summaryDelta(total, previousTotal)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Totale guadagno", fontWeight = FontWeight.SemiBold)
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.72f)
                ) {
                    Text(
                        italianTitle(month.format(monthFormatter)),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            AnimatedContent(
                targetState = total,
                transitionSpec = {
                    fadeIn(tween(250)) togetherWith fadeOut(tween(150))
                },
                label = "Totale mese"
            ) { displayedTotal ->
                Text(
                    money(displayedTotal),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "${delta.label} rispetto al mese precedente (${money(previousTotal)})",
                style = MaterialTheme.typography.bodySmall,
                color = delta.color
            )
        }
    }
}

@Composable
private fun SummaryNetCard(
    month: YearMonth,
    grossCents: Long,
    irpefBasisPoints: Long,
    manuallyEnteredCents: Long?,
    canUpdatePercentage: Boolean,
    onSaveManualNet: suspend (Long?, Long?) -> Boolean
) {
    val validBasisPoints = irpefBasisPoints.coerceIn(0L, 10_000L)
    val estimatedCents = estimatedNetCents(grossCents, validBasisPoints)
    val estimatedWithholding = grossCents - estimatedCents
    val percentLabel = "%.2f".format(Locale.ITALY, validBasisPoints / 100.0)
    var isEditing by remember(month) { mutableStateOf(false) }
    var saving by remember(month) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var manualInput by remember(month, manuallyEnteredCents) {
        mutableStateOf(manuallyEnteredCents?.toEuroText().orEmpty())
    }
    val parsedManual = parseMonthlyNetCents(manualInput)
    val updatedBasisPoints = parsedManual?.let { inferredWithholdingBasisPoints(grossCents, it) }
    val manualValid = manualInput.isBlank() ||
        (parsedManual != null && updatedBasisPoints != null && canUpdatePercentage)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Netto del mese", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Trattenuta stimata ($percentLabel%)")
                Text("- ${money(estimatedWithholding)}", color = MaterialTheme.colorScheme.error)
            }
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Netto stimato", style = MaterialTheme.typography.labelLarge)
                    AnimatedContent(
                        targetState = estimatedCents,
                        transitionSpec = {
                            fadeIn(tween(220)) togetherWith fadeOut(tween(130))
                        },
                        label = "Netto mensile stimato"
                    ) { shownCents ->
                        Text(
                            money(shownCents),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
            Text(
                "Stima semplificata: il $percentLabel% è sottratto dal lordo del calendario. " +
                    "Non è un calcolo fiscale della busta paga.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (manuallyEnteredCents != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Netto inserito dall'utente", style = MaterialTheme.typography.labelLarge)
                        Text(
                            money(manuallyEnteredCents),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (isEditing) {
                OutlinedTextField(
                    value = manualInput,
                    onValueChange = { manualInput = it },
                    label = { Text("Netto effettivo del mese (€)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !manualValid,
                    supportingText = {
                        Text(
                            when {
                                manualInput.isBlank() ->
                                    "Lascia vuoto e salva per eliminare il valore manuale; la percentuale resta invariata."
                                parsedManual == null ->
                                    "Inserisci un importo non negativo, con massimo due decimali."
                                grossCents <= 0L ->
                                    "Serve un lordo mensile maggiore di zero per aggiornare la percentuale."
                                updatedBasisPoints == null ->
                                    "Il netto deve essere compreso tra zero e il lordo, esclusi rimborsi e conguagli."
                                !canUpdatePercentage ->
                                    "Attendi il caricamento del profilo lavoratore."
                                else ->
                                    "Nuova percentuale stimata: " +
                                        "%.2f".format(Locale.ITALY, updatedBasisPoints / 100.0) +
                                        "%. Sarà usata anche per gli altri mesi."
                            }
                        )
                    }
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = { isEditing = false },
                        enabled = !saving
                    ) { Text("Annulla") }
                    Button(
                        onClick = {
                            saving = true
                            scope.launch {
                                val success = onSaveManualNet(parsedManual, updatedBasisPoints)
                                saving = false
                                if (success) isEditing = false
                            }
                        },
                        enabled = manualValid && !saving
                    ) {
                        Text(
                            when {
                                saving -> "Salvataggio…"
                                manualInput.isBlank() -> "Rimuovi netto manuale"
                                else -> "Salva netto e aggiorna %"
                            }
                        )
                    }
                }
            } else {
                TextButton(onClick = { isEditing = true }) {
                    Text(if (manuallyEnteredCents == null) "Inserisci netto manuale" else "Modifica netto manuale")
                }
            }
        }
    }
}

private fun summaryCategoryColor(id: String): Color = when (id) {
    "base" -> Color(0xFF2D7FF9)
    "turno" -> Color(0xFF159A80)
    "avviamento" -> Color(0xFFE17932)
    "disagio" -> Color(0xFFD95C69)
    "area" -> Color(0xFF7357D9)
    "doppio" -> Color(0xFFF2B01E)
    else -> Color(0xFF607D9B)
}

@Composable
private fun SummaryBreakdownCard(
    total: Long,
    categories: List<SummaryCategoryDetail>,
    onCategoryClick: (SummaryCategoryDetail) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                "Dettaglio per categoria",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            categories.forEachIndexed { index, item ->
                key(item.id) {
                    SummaryBreakdownRow(
                        label = item.label,
                        cents = item.cents,
                        total = total,
                        color = summaryCategoryColor(item.id),
                        onClick = { onCategoryClick(item) }
                    )
                    if (index < categories.lastIndex) {
                        HorizontalDivider(
                            color = Color(0xFFE9EFF7),
                            thickness = 0.6.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryBreakdownRow(
    label: String,
    cents: Long,
    total: Long,
    color: Color,
    onClick: () -> Unit
) {
    val denominator = total.coerceAtLeast(1L)
    val ratio = (cents.toFloat() / denominator.toFloat()).coerceIn(0f, 1f)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Spacer(Modifier.size(10.dp).background(color, CircleShape))
        Text(
            text = label,
            modifier = Modifier.width(98.dp),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        SummaryMotionBar(
            targetProgress = ratio,
            modifier = Modifier.weight(1f).height(7.dp),
            color = color
        )
        Text(
            money(cents),
            modifier = Modifier.width(79.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            summaryPercent(cents, total),
            modifier = Modifier.width(34.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "›",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummaryCategorySheet(
    detail: SummaryCategoryDetail,
    color: Color,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Composizione categoria",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onDismiss) {
                    Text(
                        "×",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Spacer(Modifier.size(17.dp).background(color, CircleShape))
                Text(
                    detail.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    money(detail.cents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            HorizontalDivider(color = Color(0xFFE6EDF7))
            if (detail.components.isEmpty()) {
                Text(
                    "Nessun importo registrato per questa categoria nel mese.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                detail.components.forEachIndexed { index, component ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            component.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            money(component.cents),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (index < detail.components.lastIndex) {
                        HorizontalDivider(color = Color(0xFFE9EFF7))
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Surface(
                color = Color(0xFFF0F5FF),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Totale categoria",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        money(detail.cents),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Only the graphic bar is interpolated. The monetary amount and percentage next
 * to it always show the exact, current calculation, including during animation.
 */
@Composable
private fun SummaryMotionBar(
    targetProgress: Float,
    modifier: Modifier,
    color: Color
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(targetProgress) {
        progress.animateTo(targetProgress, animationSpec = tween(300))
    }
    LinearProgressIndicator(
        progress = { progress.value },
        modifier = modifier,
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

@Composable
private fun SummaryMonthTrendCard(
    month: YearMonth,
    monthRows: List<ShiftWithPay>
) {
    val byDay = monthRows.groupBy(::rowDate)
    val weekCount = ((month.lengthOfMonth() - 1) / 7) + 1
    val weeklyTotals = (1..weekCount).map { week ->
        week to byDay
            .filterKeys { date -> ((date.dayOfMonth - 1) / 7) + 1 == week }
            .values
            .flatten()
            .sumOf { it.pay.totalPayCents }
    }
    val maxWeek = weeklyTotals.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    val bestDay = byDay.maxByOrNull { (_, rows) -> rows.sumOf { it.pay.totalPayCents } }
    val mostUsed = monthRows
        .groupingBy { calendarDisplayLabel(it) }
        .eachCount()
        .maxByOrNull { it.value }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Andamento nel mese",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            weeklyTotals.forEach { (week, value) ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Sett $week",
                        modifier = Modifier.width(44.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                    SummaryMotionBar(
                        targetProgress = (value.toFloat() / maxWeek.toFloat()).coerceIn(0f, 1f),
                        modifier = Modifier.weight(1f).height(8.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        money(value),
                        modifier = Modifier.width(74.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryInsight(
                    title = "Giorno migliore",
                    value = bestDay?.let { (date, rows) ->
                        "${italianTitle(date.format(shortDayFormatter))}\n${money(rows.sumOf { it.pay.totalPayCents })}"
                    } ?: "—",
                    modifier = Modifier.weight(1f)
                )
                SummaryInsight(
                    title = "Prestazione più usata",
                    value = mostUsed?.let { "${it.key}\n${it.value} volte" } ?: "—",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryInsight(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    ) {
        Column(
            Modifier.padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SummaryActionsCard(
    showDetails: Boolean,
    showComparison: Boolean,
    showExport: Boolean,
    locked: Boolean,
    onDetails: () -> Unit,
    onCompare: () -> Unit,
    onExport: () -> Unit,
    onToggleLock: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Azioni rapide",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                SummaryAction(
                    symbol = "≡",
                    label = if (showDetails) "Nascondi" else "Dettaglio",
                    selected = showDetails,
                    modifier = Modifier.weight(1f),
                    onClick = onDetails
                )
                SummaryAction(
                    symbol = "⇄",
                    label = "Confronta",
                    selected = showComparison,
                    modifier = Modifier.weight(1f),
                    onClick = onCompare
                )
                SummaryAction(
                    symbol = "⇩",
                    label = "Esporta",
                    selected = showExport,
                    modifier = Modifier.weight(1f),
                    onClick = onExport
                )
                SummaryAction(
                    symbol = if (locked) "□" else "▣",
                    label = if (locked) "Riapri" else "Chiudi",
                    selected = locked,
                    modifier = Modifier.weight(1f),
                    onClick = onToggleLock
                )
            }
        }
    }
}

@Composable
private fun SummaryAction(
    symbol: String,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val actionColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(180),
        label = "Sfondo azione riepilogo"
    )
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = actionColor
    ) {
        Column(
            Modifier.padding(horizontal = 5.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                symbol,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = if (selected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryDayCard(
    date: LocalDate,
    dayRows: List<ShiftWithPay>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    italianTitle(date.format(shortDayFormatter)),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    money(dayRows.sumOf { it.pay.totalPayCents }),
                    fontWeight = FontWeight.Bold
                )
            }
            dayRows.forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        displayShiftLabel(row),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        money(row.pay.totalPayCents),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private data class SummaryBreakdownItem(
    val label: String,
    val cents: Long,
    val color: Color
)

private data class SummaryDelta(
    val label: String,
    val color: Color
)

private fun summaryDelta(current: Long, previous: Long): SummaryDelta {
    if (previous == 0L) {
        return if (current == 0L) {
            SummaryDelta("—", Color(0xFF607D9B))
        } else {
            SummaryDelta("Nuovo", Color(0xFF159A80))
        }
    }
    val percent = ((current - previous) * 100.0 / previous.toDouble())
    val prefix = if (percent > 0) "+" else ""
    val color = when {
        percent > 0 -> Color(0xFF159A80)
        percent < 0 -> Color(0xFFD95C69)
        else -> Color(0xFF607D9B)
    }
    return SummaryDelta(
        "$prefix${"%.0f".format(Locale.ITALY, percent)}%",
        color
    )
}

private fun summaryPercent(value: Long, total: Long): String {
    if (total <= 0L || value <= 0L) return "0%"
    val percent = value * 100.0 / total.toDouble()
    return if (percent < 1.0) "<1%" else "${"%.0f".format(Locale.ITALY, percent)}%"
}
