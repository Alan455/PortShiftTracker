package it.alantamanti.portshifttracker.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToLong

class AllowanceCalculator {
    fun calculate(
        worker: Worker,
        shift: Shift,
        rules: List<AllowanceRule>,
        selectedManualRuleIds: Set<Long> = emptySet()
    ): PayBreakdown {
        require(shift.endEpochMillis > shift.startEpochMillis) { "Il turno deve terminare dopo l'inizio" }

        val totalMinutes = ((shift.endEpochMillis - shift.startEpochMillis) / 60_000.0).roundToLong()
        val performanceBasePay = when (shift.performanceType) {
            PerformanceType.TURNO -> when (worker.basePayMode) {
                BasePayMode.FIXED_PER_SHIFT -> worker.baseShiftCents
                BasePayMode.HOURLY -> centsForMinutes(worker.hourlyRateCents, totalMinutes)
            }
            PerformanceType.DOPPIO -> worker.doubleBaseCents
            PerformanceType.MEZZO_DOPPIO -> (worker.doubleBaseCents / 2.0).roundToLong()
        }

        // Relazione obbligatoria del motore: TUMezzo/ONmezzo implicano sempre
        // Mezza IMA. In questo modo anche import, backup o chiamate al calcolatore che
        // bypassano la UI producono un risultato coerente.
        val normalizedSelectedManualRuleIds = selectedManualRuleIds.toMutableSet().also { ids ->
            if (shift.performanceType == PerformanceType.TURNO) {
                val halfTurnSelected = rules.any {
                    it.id in ids && (it.code == "DOP_TU_MEZZO" || it.code == "DOP_ON_MEZZO")
                }
                val mezzaIma = rules.firstOrNull {
                    it.enabled &&
                        it.code == "ALT_MEZZA_IMA" &&
                        (it.performanceMask and PerformanceType.TURNO.maskBit) != 0
                }
                if (mezzaIma != null) {
                    if (halfTurnSelected) ids += mezzaIma.id else ids -= mezzaIma.id
                }
            }
        }

        // La maschera rende la compatibilità configurabile: area/disagi possono valere
        // sia per turno sia per doppio, mentre Polivalenza vale solo per il turno ordinario.
        val enabledCompatibleRules = rules.filter {
            it.enabled && (it.performanceMask and shift.performanceType.maskBit) != 0
        }
        val selectedRules = enabledCompatibleRules.filter { it.id in normalizedSelectedManualRuleIds }

        // Polivalenza: solo prestazione TURNO e solo quando esiste una vera indennità di turno.
        // Il Doppio non la riceve mai, anche se nello stesso giorno esiste anche un turno ordinario.
        val hasWorkedTurnWithTurnAllowance = shift.performanceType == PerformanceType.TURNO &&
            selectedRules.any { it.category == AllowanceCategory.TURNO }

        val applicableRules = enabledCompatibleRules
            .asSequence()
            .filter { rule ->
                when (rule.applicationMode) {
                    AllowanceApplicationMode.MANUAL -> rule.id in normalizedSelectedManualRuleIds
                    AllowanceApplicationMode.AUTO -> when (rule.autoTrigger) {
                        AllowanceAutoTrigger.NONE -> true
                        AllowanceAutoTrigger.WHEN_TURNO_SELECTED -> hasWorkedTurnWithTurnAllowance
                    }
                }
            }
            .filter { totalMinutes >= it.minimumShiftMinutes }
            .filter { roleMatches(it.roleFilter, shift.role) }
            .sortedBy { it.priority }
            .toList()

        // Congedo, Donazione sangue e INAIL sono assenze a importo unico:
        // la loro tariffa sostituisce la base e non si sommano altre indennità
        // (anche se vecchie selezioni o backup contengono voci aggiuntive).
        // IMA resta una voce diversa: può avere Disdetta casa/festiva.
        val exclusivePaidAbsence = applicableRules.firstOrNull {
            it.code == "AVV_CONGEDO" || it.code == "AVV_DS" || it.code == "AVV_INAIL"
        }
        if (exclusivePaidAbsence != null) {
            return PayBreakdown(totalMinutes, exclusivePaidAbsence.value, emptyList())
        }

        // Ferie, Malattia, IMA e Giornaliero possono sostituire la base ordinaria.
        val replacementRule = applicableRules
            .filter { it.basePayEffect == BasePayEffect.REPLACE_BASE }
            .minByOrNull { it.priority }

        val giornalieroRule = applicableRules.firstOrNull { it.code == "G" }
        val onMezzoSelected = applicableRules.any { it.code == "DOP_ON_MEZZO" }

        // Giornaliero primo turno: base fissa €90. Con ONMezzo la base Giornaliero
        // viene dimezzata a €45; Mezza IMA resta automatica perché è un TURNO.
        // Nel Doppio, DOP_G è già una REPLACE_BASE da €45 e non riceve Mezza IMA/Polivalenza.
        val basePay = when {
            shift.performanceType == PerformanceType.TURNO &&
                giornalieroRule != null &&
                onMezzoSelected -> (giornalieroRule.value / 2.0).roundToLong()
            else -> replacementRule?.value ?: performanceBasePay
        }

        // Mezza IMA dimezza soltanto l'indennità del TURNO ordinario. Area e disagi restano interi.
        // Non è compatibile con Doppio/Mezzo Doppio tramite performanceMask.
        val turnMultiplierBp = applicableRules
            .map { it.turnAllowanceMultiplierBasisPoints }
            .minOrNull() ?: 10000

        val lines = applicableRules
            .asSequence()
            .filter { it.basePayEffect != BasePayEffect.REPLACE_BASE }
            .mapNotNull { rule ->
                calculateRule(shift, rule, performanceBasePay)?.let { line ->
                    val multiplierBp = when {
                        rule.category == AllowanceCategory.TURNO && turnMultiplierBp != 10000 -> turnMultiplierBp
                        // Nel Mezzo Doppio TUTTE le indennità di turno (categoria DOPPIO)
                        // sono dimezzate, inclusa Mattina festiva. Area, Disagi,
                        // Pioggia e Avviamento restano interi.
                        shift.performanceType == PerformanceType.MEZZO_DOPPIO &&
                            rule.category == AllowanceCategory.DOPPIO -> 5000
                        else -> 10000
                    }
                    if (multiplierBp != 10000) {
                        line.copy(amountCents = ((line.amountCents * multiplierBp) / 10_000.0).roundToLong())
                    } else line
                }
            }
            .toList()

        return PayBreakdown(totalMinutes, basePay, lines)
    }

    private fun calculateRule(
        shift: Shift,
        rule: AllowanceRule,
        shiftBasePayCents: Long
    ): AllowanceLine? {
        val eligibleMinutes = eligibleMinutes(shift, rule)
        if (eligibleMinutes <= 0) return null

        val amount = when (rule.calculationType) {
            AllowanceCalculationType.FIXED_PER_SHIFT -> rule.value
            AllowanceCalculationType.PER_HOUR -> centsForMinutes(rule.value, eligibleMinutes)
            AllowanceCalculationType.PERCENT_BASE -> {
                val totalMinutes = ((shift.endEpochMillis - shift.startEpochMillis) / 60_000.0).roundToLong()
                val eligibleBase = if (totalMinutes <= 0) 0 else {
                    (shiftBasePayCents * eligibleMinutes / totalMinutes.toDouble()).roundToLong()
                }
                ((eligibleBase * rule.value) / 10_000.0).roundToLong()
            }
        }

        // Manteniamo nel riepilogo anche voci selezionate a 0 euro: servono per
        // registrare correttamente codici di turno come G/Pom/Mat.
        return AllowanceLine(rule.id, rule.name, eligibleMinutes, amount)
    }

    /**
     * Divide la prestazione per giorni locali. Gestisce correttamente notturni,
     * cambio giorno e DST tramite gli epoch millis.
     */
    fun eligibleMinutes(shift: Shift, rule: AllowanceRule): Long {
        val zone = ZoneId.of(shift.zoneId)
        val shiftStart = Instant.ofEpochMilli(shift.startEpochMillis).atZone(zone)
        val shiftEnd = Instant.ofEpochMilli(shift.endEpochMillis).atZone(zone)

        var date = shiftStart.toLocalDate()
        val lastDate = shiftEnd.minusNanos(1).toLocalDate()
        var millis = 0L

        while (!date.isAfter(lastDate)) {
            if (dateAllowed(date, rule) && effectiveDateAllowed(date, rule)) {
                val nextDay = date.plusDays(1).atStartOfDay(zone)
                val intervals = activeIntervalsForDay(date, zone, nextDay, rule)

                for ((activeStart, activeEnd) in intervals) {
                    val overlapStart = maxOf(activeStart.toInstant(), shiftStart.toInstant())
                    val overlapEnd = minOf(activeEnd.toInstant(), shiftEnd.toInstant())
                    if (overlapEnd.isAfter(overlapStart)) {
                        millis += overlapEnd.toEpochMilli() - overlapStart.toEpochMilli()
                    }
                }
            }
            date = date.plusDays(1)
        }

        return (millis / 60_000.0).roundToLong()
    }

    private fun activeIntervalsForDay(
        date: LocalDate,
        zone: ZoneId,
        nextDayStart: ZonedDateTime,
        rule: AllowanceRule
    ): List<Pair<ZonedDateTime, ZonedDateTime>> {
        val startMin = rule.windowStartMinute
        val endMin = rule.windowEndMinute
        val dayStart = date.atStartOfDay(zone)

        if (startMin == null || endMin == null || startMin == endMin) {
            return listOf(dayStart to nextDayStart)
        }

        val start = date.atTime(LocalTime.of(startMin / 60, startMin % 60)).atZone(zone)
        val end = date.atTime(LocalTime.of(endMin / 60, endMin % 60)).atZone(zone)

        return if (startMin < endMin) {
            listOf(start to end)
        } else {
            listOf(dayStart to end, start to nextDayStart)
        }
    }

    private fun dateAllowed(date: LocalDate, rule: AllowanceRule): Boolean {
        val bit = weekdayBit(date.dayOfWeek)
        return (rule.weekdayMask and bit) != 0
    }

    private fun effectiveDateAllowed(date: LocalDate, rule: AllowanceRule): Boolean {
        val epochDay = date.toEpochDay()
        val fromOk = rule.effectiveFromEpochDay?.let { epochDay >= it } ?: true
        val toOk = rule.effectiveToEpochDay?.let { epochDay <= it } ?: true
        return fromOk && toOk
    }

    private fun roleMatches(filter: String?, role: String): Boolean {
        if (filter.isNullOrBlank()) return true
        val accepted = filter.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return accepted.any { role.contains(it, ignoreCase = true) }
    }

    private fun weekdayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    private fun centsForMinutes(centsPerHour: Long, minutes: Long): Long =
        (centsPerHour * minutes / 60.0).roundToLong()
}
