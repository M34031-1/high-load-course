package ru.quipy.payments.logic.PaymentStages

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import ru.quipy.core.EventSourcingService
import ru.quipy.payments.api.PaymentAggregate
import ru.quipy.payments.logic.*
import ru.quipy.payments.logic.PaymentStages.StageMarkers.ProcessMarker
import ru.quipy.payments.logic.PaymentStages.StageResults.ProcessResult
import ru.quipy.payments.logic.gateways.PaymentGateway
import ru.quipy.payments.logic.gateways.PaymentGatewayPayload
import java.net.SocketTimeoutException
import java.time.Duration
import java.util.*

class ProcessStage(
    private val paymentESService: EventSourcingService<UUID, PaymentAggregate, PaymentAggregateState>,
    private val properties: PaymentAccountProperties,
    private val paymentGateway: PaymentGateway
) : PaymentStage<ProcessMarker, ProcessResult> {

    constructor(
        paymentESService: EventSourcingService<UUID, PaymentAggregate, PaymentAggregateState>,
        properties: PaymentAccountProperties,
        paymentGateway: PaymentGateway,
        timeout: Duration
    ) : this(paymentESService, properties, paymentGateway) {
        this.timeout = timeout
    }

    companion object {
        val logger = LoggerFactory.getLogger(PaymentExternalSystemAdapter::class.java)

        val mapper = ObjectMapper().registerKotlinModule()
    }

    private val serviceName = properties.serviceName
    private val accountName = properties.accountName
    private val requestAverageProcessingTime = properties.averageProcessingTime
    private val rateLimitPerSec = properties.rateLimitPerSec
    private val parallelRequests = properties.parallelRequests
    private val semaphore: Semaphore = Semaphore(parallelRequests)
    private var timeout: Duration? = null

    override suspend fun process(payment: Payment): ProcessResult {
        val startTime = System.currentTimeMillis()
        logger.warn("[$accountName] Submitting payment request for payment ${payment.paymentId}")

        val transactionId = UUID.randomUUID()
        logger.info("[$accountName] Submit for ${payment.paymentId} , txId: $transactionId")

        // Вне зависимости от исхода оплаты важно отметить что она была отправлена.
        // Это требуется сделать ВО ВСЕХ СЛУЧАЯХ, поскольку эта информация используется сервисом тестирования.
        paymentESService.update(payment.paymentId) {
            it.logSubmission(success = true, transactionId, now(), Duration.ofMillis(now() - payment.paymentStartedAt))
        }


        try {
            val payload = PaymentGatewayPayload(serviceName, accountName, transactionId, payment.paymentId, payment.amount)

            val response = withContext(Dispatchers.IO) { paymentGateway.makePayment(payload) }

            val body = response.use {
                mapper.readValue(response.body?.string(), ExternalSysResponse::class.java)
            }

            val result = body.result

            if (!result)
                return ProcessResult(retry = true, processingTime = System.currentTimeMillis() - startTime)

            logger.warn("[$accountName] Payment processed for txId: $transactionId, payment: ${payment.paymentId}, succeeded: ${result}, message: template message")

            // Здесь мы обновляем состояние оплаты в зависимости от результата в базе данных оплат.
            // Это требуется сделать ВО ВСЕХ ИСХОДАХ (успешная оплата / неуспешная / ошибочная ситуация)
            paymentESService.update(payment.paymentId) {
                it.logProcessing(result, now(), transactionId, reason = "Template message")
            }

        } catch (e: Exception) {
            when (e) {
                is SocketTimeoutException -> {
                    logger.error(
                        "[$accountName] Payment timeout for txId: $transactionId, payment: ${payment.paymentId}",
                        e
                    )
                    paymentESService.update(payment.paymentId) {
                        it.logProcessing(false, now(), transactionId, reason = "Request timeout.")
                    }
                }

                else -> {
                    logger.error(
                        "[$accountName] Payment failed for txId: $transactionId, payment: ${payment.paymentId}",
                        e
                    )

                    paymentESService.update(payment.paymentId) {
                        it.logProcessing(false, now(), transactionId, reason = e.message)
                    }
                }
            }
        }

        return ProcessResult(retry = false, processingTime = System.currentTimeMillis() - startTime)
    }
}