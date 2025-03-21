package ru.quipy.orders.subscribers.payment.handlers

import ru.quipy.domain.Event

interface EventHandler<TEvent: Event<*>> {
    suspend fun handle(event: TEvent)
}