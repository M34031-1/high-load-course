package ru.quipy.payments.logic.PaymentStages.StageResults

data class ProcessResult(
    val retry: Boolean,
    val processingTime: Long = 0
)