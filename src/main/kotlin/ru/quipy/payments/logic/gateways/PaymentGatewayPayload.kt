package ru.quipy.payments.logic.gateways

import java.util.UUID

data class PaymentGatewayPayload(val serviceName: String, val accountName: String, val transactionId: UUID, val paymentId: UUID, val amount: Int)