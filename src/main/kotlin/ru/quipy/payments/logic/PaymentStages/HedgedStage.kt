package ru.quipy.payments.logic.PaymentStages

import kotlinx.coroutines.*
import ru.quipy.payments.logic.PaymentStages.StageMarkers.HedgedMarker
import ru.quipy.payments.logic.PaymentStages.StageResults.ProcessResult
import java.time.Duration


class HedgedStage(
    private val next: PaymentStage<*, ProcessResult>,
    private val hedgeDelay: Duration
) : PaymentStage<HedgedMarker, ProcessResult> {

    override suspend fun process(payment: Payment): ProcessResult {

        val firstRequest = GlobalScope.async {
            next.process(payment)
        }

        delay(hedgeDelay.toMillis())

        val secondRequest = if (!firstRequest.isCompleted) {
            GlobalScope.async {
                next.process(payment)
            }
        } else null

        val winner = if (secondRequest != null) {
            selectRace(firstRequest, secondRequest)
        } else {
            firstRequest.await()
        }

        secondRequest?.cancel()

        return winner
    }


    private suspend fun selectRace(
        first: Deferred<ProcessResult>,
        second: Deferred<ProcessResult>
    ): ProcessResult = coroutineScope {
        val result = CompletableDeferred<ProcessResult>()

        listOf(first, second).forEach { req ->
            launch {
                try {
                    val r = req.await()
                    result.complete(r)
                } catch (e: Throwable) {
                    if (!result.isCompleted) {
                        result.completeExceptionally(e)
                    }
                }
            }
        }

        result.await()
    }
}
