package com.grafito.finanzas.notifications

import com.grafito.finanzas.data.BcpMailSuggestion
import com.grafito.finanzas.receipts.BcpScreenParser
import com.grafito.finanzas.receipts.BcpScreenStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BcpWindowRetryTest {
    @Test fun readsAReceiptThatAppearsAfterTheWindow() = runBlocking {
        var elapsed = 0L
        val attempts = mutableListOf<Pair<Int, Long>>()
        val receipt = BcpMailSuggestion("fictional-receipt", 1250L, 0L)
        val result = retryBcpWindow(elapsedMillis = { elapsed }, wait = { elapsed += it }) { attempt ->
            attempts.add(attempt to elapsed)
            when (attempt) {
                1 -> BcpScreenParser.Result(
                    BcpScreenStatus.WINDOW_UNAVAILABLE,
                    signals = AccessibilityReceiptReader.EVENT_WINDOW_LISTED or AccessibilityReceiptReader.EVENT_WINDOW_ROOT_MISSING
                )
                2 -> BcpScreenParser.Result(
                    BcpScreenStatus.UNAVAILABLE,
                    signals = AccessibilityReceiptReader.SOURCE_WINDOW_AVAILABLE
                )
                else -> BcpScreenParser.Result(BcpScreenStatus.DETECTED, payment = receipt)
            }
        }
        assertEquals(listOf(1 to 0L, 2 to 150L, 3 to 400L), attempts)
        assertEquals(receipt, result.payment)
    }

    @Test fun stopsWhenTheWindowDisappears() = runBlocking {
        var elapsed = 0L
        val attempts = mutableListOf<Long>()
        val result = retryBcpWindow(elapsedMillis = { elapsed }, wait = { elapsed += it }) { attempt ->
            attempts.add(elapsed)
            val signals = if (attempt == 1) AccessibilityReceiptReader.EVENT_WINDOW_ROOT_MISSING else 0
            BcpScreenParser.Result(BcpScreenStatus.WINDOW_UNAVAILABLE, signals = signals)
        }
        assertEquals(listOf(0L, 150L), attempts)
        assertNull(result.payment)
    }

    @Test fun limitsAnEmptyWindowToFourAttempts() = runBlocking {
        var elapsed = 0L
        val attempts = mutableListOf<Long>()
        val result = retryBcpWindow(elapsedMillis = { elapsed }, wait = { elapsed += it }) {
            attempts.add(elapsed)
            BcpScreenParser.Result(
                BcpScreenStatus.WINDOW_UNAVAILABLE,
                signals = AccessibilityReceiptReader.EVENT_WINDOW_ROOT_MISSING
            )
        }
        assertEquals(listOf(0L, 150L, 400L, 800L), attempts)
        assertNull(result.payment)
    }

    @Test fun stopsOnDenialAndForHistory() = runBlocking {
        val rejected = listOf(
            BcpScreenParser.Result(
                BcpScreenStatus.WINDOW_UNAVAILABLE,
                signals = AccessibilityReceiptReader.EVENT_WINDOW_ROOT_MISSING or AccessibilityReceiptReader.QUERY_DENIED
            ),
            BcpScreenParser.Result(
                BcpScreenStatus.VISIBLE,
                fields = BcpScreenParser.HISTORY_SCREEN,
                signals = AccessibilityReceiptReader.SOURCE_WINDOW_AVAILABLE
            )
        )
        rejected.forEach { expected ->
            val result = retryBcpWindow(elapsedMillis = { 0 }, wait = { error("Unexpected retry") }) { expected }
            assertEquals(expected, result)
        }
    }
}
