package ru.quipy.orders.subscribers.payment.handlers

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import ru.quipy.common.exceptions.PaymentException
import ru.quipy.orders.repository.OrderRepository
import ru.quipy.payments.api.PaymentCreatedEvent
import ru.quipy.payments.logic.PaymentService
import ru.quipy.payments.logic.now

@Service
class PaymentCreatedHandler : EventHandler<PaymentCreatedEvent> {

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var paymentService: PaymentService

    private var scope = CoroutineScope(Dispatchers.Default) //TODO: define the scope of subscriber and dispose this if necessary

    private val semaphore: Semaphore = Semaphore(10000, 0)

    val logger: Logger = LoggerFactory.getLogger(PaymentCreatedHandler::class.java)

    override suspend fun handle(event: PaymentCreatedEvent) {
        semaphore.acquire()

        scope.launch {
            try {
                val order = orderRepository.findById(event.orderId)

                if (order == null) {
                    PaymentException.paymentFailure("Order ${event.orderId} was not found.")
                }

                paymentService.submitPaymentRequest(event.paymentId, event.amount, now(), event.deadline)

            } catch (p: PaymentException) {
                logger.error("Payment exception: ${p.message}")
            }  finally {
                semaphore.release()
            }
        }
    }

}