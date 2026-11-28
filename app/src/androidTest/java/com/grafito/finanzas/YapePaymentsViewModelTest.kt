package com.grafito.finanzas

import androidx.lifecycle.viewModelScope
import com.grafito.finanzas.data.EntryType
import com.grafito.finanzas.data.TransactionEntity
import com.grafito.finanzas.data.YapeConfirmation
import com.grafito.finanzas.data.YapeReviewRepository
import com.grafito.finanzas.data.YapeSuggestion
import com.grafito.finanzas.model.PendingYapeState
import com.grafito.finanzas.model.YapePaymentsViewModel
import com.grafito.finanzas.model.YapeReviewState
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class YapePaymentsViewModelTest {
    @Test fun pendingFailureCanBeRetriedAndIsDistinctFromAnEmptyInbox() = runBlocking {
        val repository = FakeInbox()
        val model = YapePaymentsViewModel(repository) {}
        try {
            repository.failPending = true
            withTimeout(5_000) { model.pending.first { it == PendingYapeState.Failed } }

            repository.failPending = false
            model.refreshPending()
            assertEquals(PendingYapeState.Available(emptyList()),
                withTimeout(5_000) { model.pending.first { it is PendingYapeState.Available } })

            repository.notices.value = listOf(notice)
            assertEquals(PendingYapeState.Available(listOf(notice)),
                withTimeout(5_000) { model.pending.first { it == PendingYapeState.Available(listOf(notice)) } })
        } finally {
            model.viewModelScope.cancel()
        }
        Unit
    }

    @Test fun failedReviewCanRetryAndMissingNoticeCanBeClosed() = runBlocking {
        val repository = FakeInbox().apply { failLookup = true }
        val model = YapePaymentsViewModel(repository) {}
        try {
            withContext(Dispatchers.Main) { model.open(notice.id) }
            withTimeout(5_000) { model.review.first { it?.state == YapeReviewState.LoadFailed } }

            repository.failLookup = false
            withContext(Dispatchers.Main) { model.retry(notice.id) }
            withTimeout(5_000) { model.review.first { it?.state == YapeReviewState.Unavailable } }
            assertTrue(withContext(Dispatchers.Main) { model.close(notice.id) })
            assertNull(model.review.value)
        } finally {
            model.viewModelScope.cancel()
        }
        Unit
    }

    @Test fun confirmingOnlyRunsOnceAndSuccessSurvivesUnavailableNotificationService() = runBlocking {
        val repository = FakeInbox().apply { notices.value = listOf(notice) }
        val attemptedAlertCancellation = CompletableDeferred<String>()
        val model = YapePaymentsViewModel(repository) { id ->
            attemptedAlertCancellation.complete(id)
            throw SecurityException("Notification access revoked in fixture")
        }
        try {
            withContext(Dispatchers.Main) { model.open(notice.id) }
            withTimeout(5_000) { model.review.first { it?.state is YapeReviewState.Ready } }
            withContext(Dispatchers.Main) {
                model.confirm(notice.id, 2, notice.amountCents, null, null)
                assertFalse(model.close(notice.id))
                model.confirm(notice.id, 2, notice.amountCents, null, null)
                model.discard(notice.id)
            }
            val confirmation = YapeConfirmation(notice, TransactionEntity(
                id = 17, type = EntryType.INCOME, amountCents = notice.amountCents, accountId = 2
            ))
            repository.confirmResult.complete(confirmation)
            val saved = requireNotNull(withTimeout(5_000) { model.review.first { it?.state is YapeReviewState.Saved } })
            assertEquals(1, repository.confirmCalls)
            assertEquals(notice.id, attemptedAlertCancellation.await())
            assertEquals(YapeReviewState.Saved(confirmation), saved.state)
            assertTrue(model.consumeResult(saved))
            assertFalse(model.consumeResult(saved))
        } finally {
            model.viewModelScope.cancel()
        }
        Unit
    }

    private class FakeInbox : YapeReviewRepository {
        val notices = MutableStateFlow<List<YapeSuggestion>>(emptyList())
        @Volatile var failPending = false
        @Volatile var failLookup = false
        val confirmResult = CompletableDeferred<YapeConfirmation?>()
        var confirmCalls = 0

        override val pending = flow {
            if (failPending) throw IOException("Pending read failed")
            emitAll(notices)
        }

        override suspend fun findPending(id: String): YapeSuggestion? {
            if (failLookup) throw IOException("Review read failed")
            return notices.value.firstOrNull { it.id == id }
        }

        override suspend fun expireOld() = Unit
        override suspend fun discard(id: String): Int = error("Discard must not run during confirmation")

        override suspend fun confirmForReview(id: String, yapeAccountId: Long, amountCents: Long,
                                              categoryId: Long?, otherAccountId: Long?): YapeConfirmation? {
            confirmCalls++
            return confirmResult.await()
        }

        override suspend fun undoConfirmation(confirmation: YapeConfirmation) = false
    }

    private companion object {
        val notice = YapeSuggestion("yape-test", 1250, 1)
    }
}
