package com.doertutorial;

import java.time.Instant;
import java.util.UUID;

public class Order {
    private UUID id;
    private Instant created;
    private Instant modified;
    private OrderStatus status;
    private Long taskId;
    private String customer;
    private String items;
    private String rejectReason;
    private String reservationToken;
    private String paymentTransactionId;
    private String deliveryTrackingId;
    private Instant paymentTime;

    public void assignFieldsFrom(Order other) {
        id = other.id;
        created = other.created;
        modified = other.modified;
        status = other.status;
        taskId = other.taskId;
        customer = other.customer;
        items = other.items;
        rejectReason = other.rejectReason;
        reservationToken = other.reservationToken;
        paymentTransactionId = other.paymentTransactionId;
        deliveryTrackingId = other.deliveryTrackingId;
        paymentTime = other.paymentTime;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public Instant getModified() {
        return modified;
    }

    public void setModified(Instant modified) {
        this.modified = modified;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getCustomer() {
        return customer;
    }

    public void setCustomer(String customer) {
        this.customer = customer;
    }

    public String getItems() {
        return items;
    }

    public void setItems(String items) {
        this.items = items;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public String getReservationToken() {
        return reservationToken;
    }

    public void setReservationToken(String reservationToken) {
        this.reservationToken = reservationToken;
    }

    public String getPaymentTransactionId() {
        return paymentTransactionId;
    }

    public void setPaymentTransactionId(String paymentTransactionId) {
        this.paymentTransactionId = paymentTransactionId;
    }

    public String getDeliveryTrackingId() {
        return deliveryTrackingId;
    }

    public void setDeliveryTrackingId(String deliveryTrackingId) {
        this.deliveryTrackingId = deliveryTrackingId;
    }

    public Instant getPaymentTime() {
        return paymentTime;
    }

    public void setPaymentTime(Instant paymentTime) {
        this.paymentTime = paymentTime;
    }
}

