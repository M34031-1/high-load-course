package ru.quipy.payments.logic.PaymentStages

import kotlinx.coroutines.sync.Semaphore
import ru.quipy.payments.logic.PaymentStages.StageMarkers.SemaphoreMarker
import ru.quipy.payments.logic.PaymentStages.StageResults.ProcessResult

class SemaphoreStage(val next: PaymentStage<*, ProcessResult>, val semaphore: Semaphore) :
    PaymentStage<SemaphoreMarker, ProcessResult> {
    override suspend fun process(payment: Payment): ProcessResult {

        try {
            semaphore.acquire()
            return next.process(payment)
        }finally {
            semaphore.release()
        }
    }
}