package it.alantamanti.portshifttracker.ui

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity
import it.alantamanti.portshifttracker.data.local.AppFeatureStore
import it.alantamanti.portshifttracker.data.local.DayOverrideClass
import it.alantamanti.portshifttracker.data.local.PayslipComparison
import it.alantamanti.portshifttracker.data.local.ShiftPreset
import it.alantamanti.portshifttracker.data.local.SpecialDayOverride
import it.alantamanti.portshifttracker.data.repository.BackupCodec
import it.alantamanti.portshifttracker.data.repository.PortRepository
import it.alantamanti.portshifttracker.data.repository.ShiftWithPay
import it.alantamanti.portshifttracker.domain.AllowanceCategory
import it.alantamanti.portshifttracker.domain.PerformanceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToLong

private val featureDateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@Composable
internal fun PresetQuickBar(
    rules: List<AllowanceRuleEntity>,
    selectedIds: Set<Long>,
    role: String,
    performanceType: PerformanceType,
    onApply: (String, Set<Long>) -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { AppFeatureStore(context) }
    val presets by store.presetsFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var naming by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Preset mansione / area", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (presets.isEmpty()) {
                Text(
                    "Salva una combinazione ricorrente di mansione, Area, Disagi e Avviamento e richiamala con un tocco.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                val replaceCategories = setOf(AllowanceCategory.AREA, AllowanceCategory.DISAGIO, AllowanceCategory.AVVIAMENTO)
                                val preserved = selectedIds.filterTo(mutableSetOf()) { id ->
                                    rules.firstOrNull { it.id == id }?.category !in replaceCategories
                                }
                                val presetIds = rules.filter {
                                    it.enabled &&
                                        it.code in preset.ruleCodes &&
                                        it.category in replaceCategories &&
                                        (it.performanceMask and performanceType.maskBit) != 0
                                }.map { it.id }
                                onApply(preset.role.ifBlank { role }, preserved + presetIds)
                            },
                            label = { Text(preset.name) }
                        )
                    }
                }
            }
            OutlinedButton(onClick = { naming = true }, modifier = Modifier.fillMaxWidth()) {
                Text("＋ Salva selezione corrente come preset")
            }
        }
    }

    if (naming) {
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Nuovo preset") },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text("Nome preset") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val categories = setOf(AllowanceCategory.AREA, AllowanceCategory.DISAGIO, AllowanceCategory.AVVIAMENTO)
                    val codes = rules.filter { it.id in selectedIds && it.category in categories }.map { it.code }.toSet()
                    scope.launch {
                        store.upsertPreset(
                            ShiftPreset(
                                id = UUID.randomUUID().toString(),
                                name = presetName.ifBlank { role.ifBlank { "Preset" } },
                                role = role,
                                ruleCodes = codes
                            )
                        )
                        presetName = ""
                        naming = false
                    }
                }) { Text("Salva") }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Annulla") } }
        )
    }
}

@Composable
internal fun PresetSettingsCard() {
    val context = LocalContext.current
    val store = remember(context) { AppFeatureStore(context) }
    val presets by store.presetsFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    FeatureCard("Preset mansione / area") {
        Text(
            "I preset si creano direttamente dalla schermata di inserimento e possono essere eliminati qui.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (presets.isEmpty()) {
            Text("Nessun preset salvato.", style = MaterialTheme.typography.bodySmall)
        } else {
            presets.forEach { preset ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(preset.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(preset.role.takeIf { it.isNotBlank() }, "${preset.ruleCodes.size} voci").joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = {
                            scope.launch { store.deletePreset(preset.id) }
                        }) { Text("Elimina") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SpecialCalendarSettingsCard() {
    val context = LocalContext.current
    val store = remember(context) { AppFeatureStore(context) }
    val days by store.specialDaysFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var label by remember { mutableStateOf("") }
    var dayClass by remember { mutableStateOf(DayOverrideClass.FESTIVO) }

    FeatureCard("Calendario speciale") {
        Text(
            "Queste date hanno priorità sul calendario automatico. Puoi correggere festività aziendali o portuali senza aggiornare l'app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = {
            selectedDate = LocalDate.now()
            label = ""
            dayClass = DayOverrideClass.FESTIVO
            editing = true
        }, modifier = Modifier.fillMaxWidth()) { Text("＋ Aggiungi giorno speciale") }

        days.takeLast(12).forEach { day ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${day.date.format(featureDateFmt)} • ${day.dayClass.label()}", fontWeight = FontWeight.SemiBold)
                        if (day.label.isNotBlank()) Text(day.label, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = {
                        scope.launch { store.deleteSpecialDay(day.epochDay) }
                    }) { Text("Elimina") }
                }
            }
        }
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Giorno speciale") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, y, m, d -> selectedDate = LocalDate.of(y, m + 1, d) },
                                selectedDate.year,
                                selectedDate.monthValue - 1,
                                selectedDate.dayOfMonth
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(selectedDate.format(featureDateFmt)) }
                    OutlinedTextField(label, { label = it }, label = { Text("Descrizione") }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOverrideClass.entries.forEach { value ->
                            FilterChip(selected = dayClass == value, onClick = { dayClass = value }, label = { Text(value.label()) })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        store.upsertSpecialDay(SpecialDayOverride(selectedDate.toEpochDay(), label, dayClass))
                        editing = false
                    }
                }) { Text("Salva") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Annulla") } }
        )
    }
}

@Composable
internal fun PayslipComparisonCard(
    month: YearMonth,
    rows: List<ShiftWithPay>,
    rules: List<AllowanceRuleEntity>
) {
    val context = LocalContext.current
    val store = remember(context) { AppFeatureStore(context) }
    val scope = rememberCoroutineScope()
    val totals = remember(rows, rules) { monthlyCategoryTotals(rows, rules) }
    val payslips by store.payslipsFlow.collectAsState(initial = emptyList())
    val existing = payslips.firstOrNull { it.month == month.toString() }

    var totalText by remember(month) { mutableStateOf("") }
    var baseText by remember(month) { mutableStateOf("") }
    var turnoText by remember(month) { mutableStateOf("") }
    var avvText by remember(month) { mutableStateOf("") }
    var disagioText by remember(month) { mutableStateOf("") }
    var areaText by remember(month) { mutableStateOf("") }
    var doppioText by remember(month) { mutableStateOf("") }
    var altreText by remember(month) { mutableStateOf("") }
    var expanded by remember(month) { mutableStateOf(false) }
    var saved by remember(month) { mutableStateOf(false) }

    LaunchedEffect(month, existing) {
        totalText = existing?.totalCents.toEuroInput()
        baseText = existing?.baseCents.toEuroInput()
        turnoText = existing?.turnoCents.toEuroInput()
        avvText = existing?.avviamentoCents.toEuroInput()
        disagioText = existing?.disagioCents.toEuroInput()
        areaText = existing?.areaCents.toEuroInput()
        doppioText = existing?.doppioCents.toEuroInput()
        altreText = existing?.altreCents.toEuroInput()
        saved = false
    }

    FeatureCard("Confronto con busta paga") {
        val actual = parseEuro(totalText)
        ComparisonSummary("Calcolato dall'app", totals.total)
        ComparisonInput("Totale busta €", totals.total, totalText) { totalText = it }
        if (actual != null) {
            ComparisonSummary("Differenza busta - app", actual - totals.total, highlight = true)
        }
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Nascondi dettaglio" else "Confronta singole voci")
        }
        if (expanded) {
            ComparisonInput("Base €", totals.base, baseText) { baseText = it }
            ComparisonInput("Turno €", totals.turno, turnoText) { turnoText = it }
            ComparisonInput("Avviamento €", totals.avviamento, avvText) { avvText = it }
            ComparisonInput("Disagi €", totals.disagio, disagioText) { disagioText = it }
            ComparisonInput("Area €", totals.area, areaText) { areaText = it }
            ComparisonInput("Doppi €", totals.doppio, doppioText) { doppioText = it }
            ComparisonInput("Altre voci €", totals.altre, altreText) { altreText = it }
        }
        Button(
            onClick = {
                scope.launch {
                    store.savePayslip(
                        PayslipComparison(
                            month = month.toString(),
                            totalCents = parseEuro(totalText),
                            baseCents = parseEuro(baseText),
                            turnoCents = parseEuro(turnoText),
                            avviamentoCents = parseEuro(avvText),
                            disagioCents = parseEuro(disagioText),
                            areaCents = parseEuro(areaText),
                            doppioCents = parseEuro(doppioText),
                            altreCents = parseEuro(altreText),
                            locked = existing?.locked ?: false
                        )
                    )
                    saved = true
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Salva confronto") }
        OutlinedButton(
            onClick = {
                scope.launch {
                    val current = PayslipComparison(
                        month = month.toString(),
                        totalCents = parseEuro(totalText),
                        baseCents = parseEuro(baseText),
                        turnoCents = parseEuro(turnoText),
                        avviamentoCents = parseEuro(avvText),
                        disagioCents = parseEuro(disagioText),
                        areaCents = parseEuro(areaText),
                        doppioCents = parseEuro(doppioText),
                        altreCents = parseEuro(altreText),
                        locked = existing?.locked ?: false
                    )
                    store.savePayslip(current.copy(locked = !current.locked))
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (existing?.locked == true) "Riapri mese" else "Chiudi mese")
        }
        Text(
            if (existing?.locked == true)
                "Mese chiuso: modifiche e cancellazioni richiedono conferma."
            else
                "Chiudi il mese dopo averlo confrontato con la busta paga.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (saved) Text("Confronto salvato.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun MonthlyExportCard(month: YearMonth, rows: List<ShiftWithPay>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember(month) { mutableStateOf<String?>(null) }

    val xlsxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { ReportExporter.writeXlsx(context, uri, month, rows) } }
                .onSuccess { status = "Excel esportato." }
                .onFailure { status = "Errore export Excel: ${it.message}" }
        }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { ReportExporter.writePdf(context, uri, month, rows) } }
                .onSuccess { status = "PDF esportato." }
                .onFailure { status = "Errore export PDF: ${it.message}" }
        }
    }

    FeatureCard("Esporta mese") {
        Text(
            "Il report contiene una riga per prestazione con base, indennità, totale, mansione e dettaglio voci.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = { xlsxLauncher.launch("PortShiftTracker-${month}.xlsx") },
            modifier = Modifier.fillMaxWidth(),
            enabled = rows.isNotEmpty()
        ) { Text("Esporta Excel (.xlsx)") }
        OutlinedButton(
            onClick = { pdfLauncher.launch("PortShiftTracker-${month}.pdf") },
            modifier = Modifier.fillMaxWidth(),
            enabled = rows.isNotEmpty()
        ) { Text("Esporta PDF") }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
internal fun BackupSettingsCard(repository: PortRepository) {
    val context = LocalContext.current
    val store = remember(context) { AppFeatureStore(context) }
    val scope = rememberCoroutineScope()
    var pendingJson by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = pendingJson
        if (uri != null && json != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                        ?: error("File non disponibile")
                }
            }.onSuccess { status = "Backup salvato." }
                .onFailure { status = "Errore backup: ${it.message}" }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("File non disponibile")
                }
                val decoded = BackupCodec.decode(text)
                repository.restoreSnapshot(decoded.database)
                store.importJson(decoded.featureStoreJson)
            }.onSuccess { status = "Backup ripristinato. Dati e impostazioni sono stati ricaricati." }
                .onFailure { status = "Backup non valido: ${it.message}" }
        }
    }

    FeatureCard("Backup e ripristino") {
        Text(
            "Il backup automatico Android è abilitato. Puoi anche creare un backup completo manuale con turni, tariffe, preset, calendario speciale e confronti busta.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = {
                scope.launch {
                    runCatching { BackupCodec.encode(repository.exportSnapshot(), store.exportJson()) }
                        .onSuccess {
                            pendingJson = it
                            createLauncher.launch("PortShiftTracker-backup-${LocalDate.now()}.json")
                        }
                        .onFailure { status = "Errore preparazione backup: ${it.message}" }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Crea backup completo") }
        OutlinedButton(
            onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Ripristina backup") }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun FeatureCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ComparisonSummary(label: String, cents: Long, highlight: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            fmtMoney(cents),
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ComparisonInput(label: String, appCents: Long, value: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label.removeSuffix(" €"), style = MaterialTheme.typography.labelMedium)
            Text("App ${fmtMoney(appCents)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text("Busta €") },
            singleLine = true,
            modifier = Modifier.width(132.dp)
        )
    }
}

private fun DayOverrideClass.label(): String = when (this) {
    DayOverrideClass.FERIALE -> "Feriale"
    DayOverrideClass.SABATO -> "Sabato"
    DayOverrideClass.FESTIVO -> "Festivo"
    DayOverrideClass.SEMIFESTIVO -> "Semifestivo"
}

private fun Long?.toEuroInput(): String = this?.let { "%.2f".format(Locale.US, it / 100.0) }.orEmpty()
private fun parseEuro(text: String): Long? = text.trim().takeIf { it.isNotEmpty() }?.replace(',', '.')?.toDoubleOrNull()?.times(100)?.roundToLong()
private fun fmtMoney(cents: Long): String = "€ %.2f".format(Locale.ITALY, cents / 100.0)
