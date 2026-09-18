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
internal fun SettingsScreen(repository: PortRepository) {
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

