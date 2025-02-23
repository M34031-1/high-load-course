package ru.quipy.orders.subscribers.payment.config

import kotlinx.coroutines.sync.Semaphore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PaymentSemaphoreConfig {

    @Bean
    fun paymentSemaphore(): Semaphore {
        val permits = 10
        val acquiredPermits = 0
        return Semaphore(permits, acquiredPermits)
    }
}
