package it.alantamanti.portshifttracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import it.alantamanti.portshifttracker.data.repository.ShiftWithPay
import it.alantamanti.portshifttracker.domain.AllowanceCategory
import it.alantamanti.portshifttracker.domain.PerformanceType
import java.time.YearMonth

@Composable
internal fun StatisticsCard(
    month: YearMonth,
    monthRows: List<ShiftWithPay>,
    trendRows: List<ShiftWithPay>
) {
    val turni = monthRows.count { it.shift.performanceType == PerformanceType.TURNO }
    val doppi = monthRows.count { it.shift.performanceType == PerformanceType.DOPPIO }
    val mezziDoppi = monthRows.count { it.shift.performanceType == PerformanceType.MEZZO_DOPPIO }
    val ferie = monthRows.count { row -> row.selectedRules.any { it.code == "ALT_FERIE" } }
    val malattia = monthRows.count { row -> row.selectedRules.any { it.code == "ALT_MALATTIA" } }
    val avviamenti = monthRows.sumOf { row ->
        row.selectedRules.count { it.category == AllowanceCategory.AVVIAMENTO }
    }

    val topRules = remember(monthRows) {
        monthRows.flatMap { it.selectedRules }
            .filterNot {
                it.category == AllowanceCategory.TURNO ||
                    it.category == AllowanceCategory.DOPPIO
            }
            .groupingBy { it.name }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(3)
    }

    val monthlyTrend = remember(trendRows, month) {
        (5L downTo 0L).map { offset ->
            val ym = month.minusMonths(offset)
            ym to trendRows.filter { YearMonth.from(rowDate(it)) == ym }.sumOf { it.pay.totalPayCents }
        }
    }
    val maxTrend = monthlyTrend.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Statistiche", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatValue("Turni", turni.toString(), Modifier.weight(1f))
                StatValue("Doppi", doppi.toString(), Modifier.weight(1f))
                StatValue("½ Doppi", mezziDoppi.toString(), Modifier.weight(1f))
                StatValue("Avviam.", avviamenti.toString(), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatValue("Ferie", ferie.toString(), Modifier.weight(1f))
                StatValue("Malattia", malattia.toString(), Modifier.weight(1f))
                StatValue("Indennità", money(monthRows.sumOf { it.pay.allowancesCents }), Modifier.weight(1f))
            }

            if (topRules.isNotEmpty()) {
                Text("Più frequenti", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    topRules.joinToString(" · ") { "${it.key} ×${it.value}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text("Andamento ultimi 6 mesi", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            monthlyTrend.forEach { (ym, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(ym.month.name.take(3), style = MaterialTheme.typography.labelSmall)
                    Text(money(value), style = MaterialTheme.typography.labelSmall)
                }
                LinearProgressIndicator(
                    progress = { (value.toFloat() / maxTrend.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun StatValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
