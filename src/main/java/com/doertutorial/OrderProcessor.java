package com.doertutorial;

import com.doer.*;
import com.doertutorial.Bank.Check;
import com.doertutorial.Warehouse.Reservation;
import com.doertutorial.Warehouse.TrackId;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.sql.SQLException;

@ApplicationScoped
public class OrderProcessor {
    public static final String NEW_ORDER_CREATED = "New order created";
    public static final String ORDER_PROCESSING_STARTED = "Order processing started";
    public static final String GOODS_RESERVED = "Goods reserved";
    public static final String NO_GOODS = "No Goods";
    public static final String REJECTED_NO_GOODS = "Rejected No Goods";
    public static final String PAYMENT_FAILED = "Payment failed";
    public static final String ORDER_PAID = "Order paid";
    public static final String REJECTED_NO_PAYMENT = "Rejected No Payment";
    public static final String ORDER_NOT_SHIPPED = "Order Not shipped";
    public static final String ORDER_SHIPPED = "Order shipped";
    public static final String REJECTED_NO_SHIPPING = "Rejected No Shipping";
    public static final String PAYMENT_CANCELLED = "Payment cancelled";
    public static final String RESERVATION_CANCELLED = "Reservation cancelled";

    @Inject
    DoerService doerService;
    @Inject
    OrderDao orderDao;
    @Inject
    @RestClient
    Warehouse warehouse;
    @Inject
    @RestClient
    Bank bank;

    @Transactional
    public void saveNewOrder(Order order) throws SQLException {
        Task task = new Task();
        task.setStatus(NEW_ORDER_CREATED);
        doerService.insert(task);
        order.setTaskId(task.getId());
        order.setStatus(OrderStatus.NEW);
        orderDao.insertOrder(order);
    }

    @AcceptStatus(NEW_ORDER_CREATED)
    public void startOrderProcessing(Task task, Order order) {
        order.setStatus(OrderStatus.PROCESSING);
        task.setStatus(ORDER_PROCESSING_STARTED);
    }

    @AcceptStatus(ORDER_PROCESSING_STARTED)
    @OnException(retry = "every 2m during 10m", setStatus = NO_GOODS)
    public void reserveGoods(Task task, Order order) {
        Reservation reservation = warehouse.reserveGoods(order);
        order.setReservationToken(reservation.token());
        task.setStatus(GOODS_RESERVED);
    }

    @AcceptStatus(NO_GOODS)
    public void reportNoGoodsForOrder(Task task, Order order) {
        order.setRejectReason("Cannot reserve goods for this order.");
        task.setStatus(REJECTED_NO_GOODS);
    }

    @AcceptStatus(GOODS_RESERVED)
    @OnException(retry = "every 5m during 30m", setStatus = PAYMENT_FAILED)
    public void payOrder(Task task, Order order) {
        Check check = bank.processPayment(order);
        order.setPaymentTransactionId(check.transactionId());
        task.setStatus(ORDER_PAID);
    }

    @AcceptStatus(PAYMENT_FAILED)
    public void reportNoPaymentForOrder(Task task, Order order) {
        order.setRejectReason("Payment not processed.");
        task.setStatus(REJECTED_NO_PAYMENT);
    }

    @AcceptStatus(ORDER_PAID)
    @OnException(retry = "every 2m during 10m", setStatus = ORDER_NOT_SHIPPED)
    public void shipOrder(Task task, Order order) {
        TrackId trackId = warehouse.shipTheOrder(order);
        order.setDeliveryTrackingId(trackId.token());
        task.setStatus(ORDER_SHIPPED);
    }

    @AcceptStatus(ORDER_NOT_SHIPPED)
    public void reportOrderNotShipped(Task task, Order order) {
        order.setRejectReason("Unable to ship the order.");
        task.setStatus(REJECTED_NO_SHIPPING);
    }

    @AcceptStatus(ORDER_SHIPPED)
    public void finishOrderProcessing(Task task, Order order) {
        Log.infof("Order shipped %s", order.getId());
        order.setStatus(OrderStatus.SHIPPED);
        task.setStatus(null);
    }

    @AcceptStatus(REJECTED_NO_GOODS)
    @AcceptStatus(REJECTED_NO_PAYMENT)
    @AcceptStatus(REJECTED_NO_SHIPPING)
    @OnException(retry = "every 5m during 30m", setStatus = PAYMENT_CANCELLED)
    public void cancelPayment(Task task, Order order) {
        if (order.getPaymentTransactionId() != null) {
            Check check = new Check(order.getPaymentTransactionId());
            bank.cancelPayment(check);
        }
        task.setStatus(PAYMENT_CANCELLED);
    }

    @AcceptStatus(PAYMENT_CANCELLED)
    @OnException(retry = "every 2m during 10m", setStatus = RESERVATION_CANCELLED)
    public void cancelReservation(Task task, Order order) {
        if (order.getReservationToken() != null) {
            Reservation reservation = new Reservation(order.getReservationToken());
            warehouse.cancelReservation(reservation);
        }
        task.setStatus(RESERVATION_CANCELLED);
    }

    @AcceptStatus(RESERVATION_CANCELLED)
    public void rejectOrder(Task task, Order order) {
        order.setStatus(OrderStatus.REJECTED);
        task.setStatus(null);
    }
}
