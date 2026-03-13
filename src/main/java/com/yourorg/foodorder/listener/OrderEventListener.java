package com.yourorg.foodorder.listener;

import com.yourorg.foodorder.event.*;
import com.yourorg.foodorder.service.OrderLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final OrderLifecycleService orderLifecycleService;

    public OrderEventListener(OrderLifecycleService orderLifecycleService) {
        this.orderLifecycleService = orderLifecycleService;
    }

    /**
     * On OrderCreatedEvent → validate the order exists and log.
     */
    @EventListener
    @Order(1)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("[OrderEventListener] OrderCreatedEvent received - orderId={}, userId={}",
                event.getOrderId(), event.getUserId());
        // Validation logic: order already persisted at this point.
        // Additional checks (e.g., inventory reservation) would go here.
    }

    /**
     * On PaymentCompletedEvent → automatically confirm the order.
     */
    @EventListener
    @Order(1)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("[OrderEventListener] PaymentCompletedEvent received - orderId={}", event.getOrderId());
        orderLifecycleService.confirmOrder(event.getOrderId());
    }

    /**
     * On OrderConfirmedEvent → automatically mark order as PREPARING.
     */
    @EventListener
    @Order(1)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        log.info("[OrderEventListener] OrderConfirmedEvent received - orderId={}", event.getOrderId());
        orderLifecycleService.markPreparing(event.getOrderId());
    }

    /**
     * On OrderPreparedEvent → automatically mark order as READY.
     */
    @EventListener
    @Order(1)
    public void onOrderPrepared(OrderPreparedEvent event) {
        log.info("[OrderEventListener] OrderPreparedEvent received - orderId={}", event.getOrderId());
        orderLifecycleService.markReady(event.getOrderId());
    }

    /**
     * On OrderDeliveredEvent → log final completion.
     */
    @EventListener
    @Order(1)
    public void onOrderDelivered(OrderDeliveredEvent event) {
        log.info("[OrderEventListener] OrderDeliveredEvent received - orderId={} COMPLETED",
                event.getOrderId());
    }
}