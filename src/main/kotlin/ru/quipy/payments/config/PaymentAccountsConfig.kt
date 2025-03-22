package ru.quipy.payments.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.sync.Semaphore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.quipy.common.utils.RateLimiter
import ru.quipy.common.utils.SlidingWindowRateLimiter
import ru.quipy.core.EventSourcingService
import ru.quipy.payments.api.PaymentAggregate
import ru.quipy.payments.logic.PaymentAccountProperties
import ru.quipy.payments.logic.PaymentAggregateState
import ru.quipy.payments.logic.PaymentExternalSystemAdapter
import ru.quipy.payments.logic.PaymentExternalSystemAdapterImpl
import ru.quipy.payments.logic.PaymentStages.ProcessStage
import ru.quipy.payments.logic.PaymentStages.RateLimitStage
import ru.quipy.payments.logic.PaymentStages.SemaphoreStage
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.*


@Configuration
class PaymentAccountsConfig {
    companion object {
        private val PAYMENT_PROVIDER_HOST_PORT: String = "localhost:1234"
        private val javaClient = HttpClient.newBuilder().build()
        private val mapper = ObjectMapper().registerKotlinModule().registerModules(JavaTimeModule())
    }

    private val allowedAccounts = setOf("acc-9")

    private val accountLimiters = mapOf<String, RateLimiter>(
        Pair("acc-9", SlidingWindowRateLimiter(110, Duration.ofMillis(1000))),
    )

    private val accountTimeouts = mapOf<String, Duration>(
        Pair("acc-9", Duration.ofMillis(1000)),
    )

    private val accountSemaphores = mapOf<String, Semaphore>(
        Pair("acc-9", Semaphore(permits = 50))
    )

    private val accountRetry = mapOf<String, Int>(
        Pair("acc-9", 2)
    )

    private fun paymentStages(
        properties: PaymentAccountProperties,
        paymentService: EventSourcingService<UUID, PaymentAggregate, PaymentAggregateState>,
        rateLimiter: RateLimiter,
        timeout: Duration,
        semaphore: Semaphore,
        retry: Int
    ) =
        RateLimitStage(
            next = SemaphoreStage(
                next = ProcessStage(
                    paymentService,
                    properties,
                    timeout
                ),
                semaphore = semaphore
            ),
            rateLimiter = rateLimiter
        )


    @Bean
    fun accountAdapters(paymentService: EventSourcingService<UUID, PaymentAggregate, PaymentAggregateState>): List<PaymentExternalSystemAdapter> {
        val request = HttpRequest.newBuilder()
            .uri(URI("http://${PAYMENT_PROVIDER_HOST_PORT}/external/accounts?serviceName=onlineStore")) // todo sukhoa service name
            .GET()
            .build()

        val resp = javaClient.send(request, HttpResponse.BodyHandlers.ofString())

        println("\nPayment accounts list:")
        return mapper.readValue<List<PaymentAccountProperties>>(
            resp.body(),
            mapper.typeFactory.constructCollectionType(List::class.java, PaymentAccountProperties::class.java)
        )
            .filter {
                it.accountName in allowedAccounts
            }.onEach(::println)
            .map {
                PaymentExternalSystemAdapterImpl(
                    it,
                    paymentStages(
                        it,
                        paymentService,
                        accountLimiters[it.accountName]!!,
                        accountTimeouts[it.accountName]!!,
                        accountSemaphores[it.accountName]!!,
                        accountRetry[it.accountName]!!
                    )
                )
            }
    }
}