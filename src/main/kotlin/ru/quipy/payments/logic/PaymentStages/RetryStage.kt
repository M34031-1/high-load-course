package ru.quipy.payments.logic.PaymentStages

import kotlinx.coroutines.delay
import ru.quipy.payments.logic.PaymentStages.StageMarkers.RetryMarker
import ru.quipy.payments.logic.PaymentStages.StageResults.ProcessResult


class RetryStage(
    val next: PaymentStage<*, ProcessResult>,
    val retryTimes: Int = 3
) : PaymentStage<RetryMarker, ProcessResult> {

    override suspend fun process(payment: Payment): ProcessResult {
        for (x in 0 until retryTimes) {
            val result = next.process(payment)

            if (result.retry)
                continue



            return result
        }
        return ProcessResult(retry = false)
    }

}