package ru.quipy.payments.logic.gateways

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Service
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

@Service
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
class PaymentGateway {
    private val connectionPool = ConnectionPool(10000, 50_000, TimeUnit.MILLISECONDS)
    private val dispatcher: Dispatcher
    private val client: OkHttpClient

    init {
        dispatcher = Dispatcher()
        dispatcher.maxRequests = 20000
        dispatcher.maxRequestsPerHost = 20000

        client = OkHttpClient.Builder()
            .protocols(Collections.singletonList(Protocol.H2_PRIOR_KNOWLEDGE))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .connectionPool(connectionPool)
            .dispatcher(dispatcher)
            .build()
    }

    companion object {
        val logger = LoggerFactory.getLogger(PaymentGateway::class.java)

        val emptyBody = RequestBody.create(null, ByteArray(0))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun makePayment(payload: PaymentGatewayPayload): Response =
        suspendCancellableCoroutine<Response> { continuation ->
            val request = Request.Builder()
                .url("http://localhost:1234/external/process?serviceName=${payload.serviceName}&accountName=${payload.accountName}&transactionId=${payload.transactionId}&paymentId=${payload.paymentId}&amount=${payload.amount}")
                .post(emptyBody)
                .build()

            logger.error("Pushing a request to client")
            client.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    try {
                        continuation.resume(response) { throwable ->
                            call.cancel()
                            response.close()
                            logger.error(
                                "Payment cancelled: ${payload.paymentId}, txId: ${payload.transactionId}",
                                throwable
                            )
                            continuation.resumeWithException(throwable)
                        }
                    } catch (e: Exception) {
                        response.close()
                        logger.error("Payment failed: ${payload.paymentId}, txId: ${payload.transactionId}", e)
                        continuation.resumeWithException(e)
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    logger.error(
                        "Payment failed with an IOException: ${payload.paymentId}, txId: ${payload.transactionId}",
                        e
                    )
                    continuation.resumeWithException(e)
                }
            })
        }
}