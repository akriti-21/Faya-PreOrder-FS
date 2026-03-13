package com.yourorg.foodorder.listener;

import com.yourorg.foodorder.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    @EventListener
    @Order(2)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("[NOTIFICATION] Notification sent to user {} for order {} - " +
                "Your order has been placed successfully!", event.getUserId(), event.getOrderId());
    }

    @EventListener
    @Order(2)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("[NOTIFICATION] Notification sent to user {} for order {} - " +
                "Payment received! Your order is being confirmed.", event.getUserId(), event.getOrderId());
    }

    @EventListener
    @Order(2)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        log.info("[NOTIFICATION] Notification sent to user {} for order {} - " +
                "Your order has been confirmed and is now being prepared.", event.getUserId(), event.getOrderId());
    }

    @EventListener
    @Order(2)
    public void onOrderPrepared(OrderPreparedEvent event) {
        log.info("[NOTIFICATION] Notification sent to user {} for order {} - " +
                "Your order is ready and will be delivered shortly!", event.getUserId(), event.getOrderId());
    }

    @EventListener
    @Order(2)
    public void onOrderDelivered(OrderDeliveredEvent event) {
        log.info("[NOTIFICATION] Notification sent to user {} for order {} - " +
                "Your order has been delivered. Enjoy your meal!", event.getUserId(), event.getOrderId());
    }
}
