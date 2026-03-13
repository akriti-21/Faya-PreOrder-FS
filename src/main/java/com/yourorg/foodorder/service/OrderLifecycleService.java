package com.yourorg.foodorder.service;

import com.yourorg.foodorder.domain.Order;
import com.yourorg.foodorder.domain.OrderEventLog;
import com.yourorg.foodorder.domain.OrderStatus;
import com.yourorg.foodorder.dto.OrderTimelineEventDto;
import com.yourorg.foodorder.dto.OrderTimelineResponse;
import com.yourorg.foodorder.event.*;
import com.yourorg.foodorder.exception.BusinessException;
import com.yourorg.foodorder.repository.OrderEventLogRepository;
import com.yourorg.foodorder.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(OrderLifecycleService.class);

    private final OrderRepository orderRepository;
    private final OrderEventLogRepository orderEventLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderLifecycleService(OrderRepository orderRepository,
                                  OrderEventLogRepository orderEventLogRepository,
                                  ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderEventLogRepository = orderEventLogRepository;
        this.eventPublisher = eventPublisher;
    }

    // ─── Order Creation ──────────────────────────────────────────────────────────

    @Transactional
    public Order createOrder(Order order) {
        order.setStatus(OrderStatus.PENDING);
        Order saved = orderRepository.save(order);
        recordOrderEvent(saved.getId(), "ORDER_CREATED", "Order placed successfully");
        publishOrderEvent(new OrderCreatedEvent(saved.getId(), saved.getUserId()));
        return saved;
    }

    // ─── Payment ─────────────────────────────────────────────────────────────────

    @Transactional
    public void handlePaymentSuccess(UUID orderId) {
        Order order = fetchOrder(orderId);
        recordOrderEvent(orderId, "PAYMENT_COMPLETED", "Payment processed successfully");
        publishOrderEvent(new PaymentCompletedEvent(orderId, order.getUserId()));
    }

    // ─── Status Transitions ───────────────────────────────────────────────────────

    @Transactional
    public Order confirmOrder(UUID orderId) {
        return transitionStatus(orderId, OrderStatus.CONFIRMED,
                "ORDER_CONFIRMED", "Order confirmed by vendor");
    }

    @Transactional
    public Order markPreparing(UUID orderId) {
        return transitionStatus(orderId, OrderStatus.PREPARING,
                "ORDER_PREPARING", "Order is being prepared");
    }

    @Transactional
    public Order markReady(UUID orderId) {
        return transitionStatus(orderId, OrderStatus.READY,
                "ORDER_PREPARED", "Order is ready for delivery");
    }

    @Transactional
    public Order markDelivered(UUID orderId) {
        return transitionStatus(orderId, OrderStatus.DELIVERED,
                "ORDER_DELIVERED", "Order delivered to customer");
    }

    @Transactional
    public Order cancelOrder(UUID orderId) {
        return transitionStatus(orderId, OrderStatus.CANCELLED,
                "ORDER_CANCELLED", "Order has been cancelled");
    }

    // ─── Timeline ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OrderTimelineResponse getOrderTimeline(UUID orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new BusinessException("Order not found: " + orderId);
        }
        List<OrderTimelineEventDto> events = orderEventLogRepository
                .findByOrderIdOrderByCreatedAtAsc(orderId)
                .stream()
                .map(log -> OrderTimelineEventDto.builder()
                        .eventType(log.getEventType())
                        .description(log.getDescription())
                        .timestamp(log.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
        return new OrderTimelineResponse(orderId, events);
    }

    // ─── Internal Helpers ────────────────────────────────────────────────────────

    private Order transitionStatus(UUID orderId, OrderStatus target,
                                    String eventType, String description) {
        Order order = fetchOrder(orderId);
        order.getStatus().validateTransitionTo(target);
        order.setStatus(target);
        Order saved = orderRepository.save(order);
        recordOrderEvent(orderId, eventType, description);
        publishStatusEvent(saved);
        return saved;
    }

    public void publishOrderEvent(Object event) {
        log.debug("Publishing event: {}", event.getClass().getSimpleName());
        eventPublisher.publishEvent(event);
    }

    public void recordOrderEvent(UUID orderId, String eventType, String description) {
        OrderEventLog entry = OrderEventLog.builder()
                .orderId(orderId)
                .eventType(eventType)
                .description(description)
                .build();
        orderEventLogRepository.save(entry);
        log.info("Timeline recorded [orderId={}, eventType={}]", orderId, eventType);
    }

    private void publishStatusEvent(Order order) {
        UUID orderId = order.getId();
        UUID userId = order.getUserId();
        switch (order.getStatus()) {
            case CONFIRMED  -> publishOrderEvent(new OrderConfirmedEvent(orderId, userId));
            case READY      -> publishOrderEvent(new OrderPreparedEvent(orderId, userId));
            case DELIVERED  -> publishOrderEvent(new OrderDeliveredEvent(orderId, userId));
            default         -> log.debug("No domain event mapped for status: {}", order.getStatus());
        }
    }

    private Order fetchOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("Order not found: " + orderId));
    }
}