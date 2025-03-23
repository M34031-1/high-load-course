package ru.quipy.payments.logic.PaymentStages

import ru.quipy.core.EventSourcingService
import ru.quipy.payments.api.PaymentAggregate
import ru.quipy.payments.logic.PaymentAggregateState
import ru.quipy.payments.logic.PaymentStages.StageMarkers.ShortcircuitMarker
import ru.quipy.payments.logic.PaymentStages.StageResults.ProcessResult
import ru.quipy.payments.logic.logProcessing
import ru.quipy.payments.logic.now
import java.util.UUID
import java.time.Duration

class ShortcircuitStage(
    val next: PaymentStage<*, ProcessResult>,
    private val paymentESService: EventSourcingService<UUID, PaymentAggregate, PaymentAggregateState>,
    val averageExecutionDuration: Duration,
) : PaymentStage<ShortcircuitMarker, ProcessResult> {
    override suspend fun process(payment: Payment) : ProcessResult{
        if (payment.paymentStartedAt + averageExecutionDuration.toMillis() >= payment.deadline) {
            paymentESService.update(payment.paymentId) {
                it.logProcessing(false, now(), null, reason = "Request short circuited.")
            }

            return ProcessResult(retry = false)
        }

        return next.process(payment)
    }
}