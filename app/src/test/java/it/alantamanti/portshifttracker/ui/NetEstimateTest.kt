package it.alantamanti.portshifttracker.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetEstimateTest {
    @Test
    fun thirtyPercentOfGrossIsDisplayedInCents() {
        assertEquals(172_754L, estimatedNetCents(246_791L, 3_000L))
    }

    @Test
    fun updatesWhenPercentageChanges() {
        assertEquals(180_000L, estimatedNetCents(240_000L, 2_500L))
        assertEquals(168_000L, estimatedNetCents(240_000L, 3_000L))
        assertEquals(156_000L, estimatedNetCents(240_000L, 3_500L))
    }

    @Test
    fun zeroAndFullPercentAndHalfCentRounding() {
        assertEquals(123_456L, estimatedNetCents(123_456L, 0L))
        assertEquals(0L, estimatedNetCents(123_456L, 10_000L))
        assertEquals(1L, estimatedNetCents(1L, 5_000L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPercentGreaterThanOneHundred() {
        estimatedNetCents(100L, 10_001L)
    }

    @Test
    fun manualNetAcceptsCommaOrPeriodAndCanBeCleared() {
        assertEquals(180_000L, parseMonthlyNetCents("1800,00"))
        assertEquals(180_050L, parseMonthlyNetCents("1800.50"))
        assertEquals(180_000L, parseMonthlyNetCents("1800"))
        assertNull(parseMonthlyNetCents(" "))
    }

    @Test
    fun manualNetRejectsInvalidAmounts() {
        assertNull(parseMonthlyNetCents("12,345"))
        assertNull(parseMonthlyNetCents("-5"))
        assertNull(parseMonthlyNetCents("abc"))
    }
}
