package it.alantamanti.portshifttracker.ui

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Stima forfettaria: la percentuale salvata come IRPEF è sottratta
 * direttamente dal lordo mensile. NON è un calcolo dell'IRPEF legale.
 * Tutti gli importi sono in centesimi; 3000 basis points = 30,00%.
 */
internal fun estimatedNetCents(grossCents: Long, irpefBasisPoints: Long): Long {
    require(irpefBasisPoints in 0L..10_000L) { "La percentuale deve essere tra 0 e 100" }
    return BigDecimal.valueOf(grossCents)
        .multiply(BigDecimal.valueOf(10_000L - irpefBasisPoints))
        .divide(BigDecimal.valueOf(10_000L), 0, RoundingMode.HALF_UP)
        .longValueExact()
}

/**
 * Calibra la percentuale forfettaria sul netto ordinario comunicato dall'utente.
 * 10_000 basis points = 100%; null significa che il lordo e il netto
 * non permettono di ricavare una percentuale nell'intervallo 0..100%.
 */
internal fun inferredWithholdingBasisPoints(grossCents: Long, manualNetCents: Long): Long? {
    if (grossCents <= 0L || manualNetCents < 0L || manualNetCents > grossCents) return null
    return BigDecimal.valueOf(grossCents - manualNetCents)
        .multiply(BigDecimal.valueOf(10_000L))
        .divide(BigDecimal.valueOf(grossCents), 0, RoundingMode.HALF_UP)
        .longValueExact()
}

/** Consente 1800, 1800.50 o 1800,50: vuoto significa 'nessun netto manuale'. */
internal fun parseMonthlyNetCents(text: String): Long? {
    val normalized = text.trim().replace(',', '.')
    if (normalized.isEmpty()) return null
    if (!Regex("""\d+(?:\.\d{1,2})?""").matches(normalized)) return null
    return runCatching {
        BigDecimal(normalized).movePointRight(2).longValueExact()
    }.getOrNull()
}
