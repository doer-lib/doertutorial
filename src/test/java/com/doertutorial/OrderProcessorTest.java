package com.doertutorial;

import com.doer.DoerAccessor;
import com.doer.DoerService;
import com.doer.Task;
import com.doertutorial.Bank.Check;
import com.doertutorial.Warehouse.Reservation;
import com.doertutorial.Warehouse.TrackId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.doertutorial.OrderProcessor.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderProcessorTest {

    @Mock
    DoerService doerService;
    @Mock
    OrderDao orderDao;
    @Mock
    Warehouse warehouse;
    @Mock
    Bank bank;

    @InjectMocks
    OrderProcessor orderProcessor;

    Task task;
    Order order;

    @BeforeEach
    void init() {
        task = new Task();
        DoerAccessor.assignTaskId(task, 17L);
        task.setStatus("Test status");
        order = new Order();
    }

    @Test
    void saveNewOrder__should_create_task() throws Exception {
        doAnswer(i -> {
            task = i.getArgument(0);
            DoerAccessor.assignTaskId(task, 720L);
            return null;
        }).when(doerService).insert(any(Task.class));

        orderProcessor.saveNewOrder(order);

        assertEquals(720L, order.getTaskId());
        assertEquals(OrderStatus.NEW, order.getStatus());
        verify(orderDao).insertOrder(order);
        assertEquals(NEW_ORDER_CREATED, task.getStatus());
    }

    @Test
    void startOrderProcessing__should_update_order_status() {
        orderProcessor.startOrderProcessing(task, order);

        assertEquals(OrderStatus.PROCESSING, order.getStatus());
        assertEquals(ORDER_PROCESSING_STARTED, task.getStatus());
    }

    @Test
    void reserveGoods__should_store_reservation_token() {
        when(warehouse.reserveGoods(order))
                .thenReturn(new Reservation("test-reservation-token"));

        orderProcessor.reserveGoods(task, order);

        assertEquals("test-reservation-token", order.getReservationToken());
        assertEquals(GOODS_RESERVED, task.getStatus());
    }

    @Test
    void reportNoGoodsForOrder__should_update_reject_reason() {
        orderProcessor.reportNoGoodsForOrder(task, order);

        assertEquals("Cannot reserve goods for this order.", order.getRejectReason());
        assertEquals(REJECTED_NO_GOODS, task.getStatus());
    }

    @Test
    void payOrder__should_store_transaction_id() {
        when(bank.processPayment(order))
                .thenReturn(new Check("test-transaction-id"));

        orderProcessor.payOrder(task, order);

        assertEquals("test-transaction-id", order.getPaymentTransactionId());
        assertEquals(ORDER_PAID, task.getStatus());
    }

    @Test
    void reportNoPaymentForOrder__should_update_reject_reason() {

        orderProcessor.reportNoPaymentForOrder(task, order);

        assertEquals("Payment not processed.", order.getRejectReason());
        assertEquals(REJECTED_NO_PAYMENT, task.getStatus());
    }

    @Test
    void shipOrder__should_save_tracking_id() {
        when(warehouse.shipTheOrder(order))
                .thenReturn(new TrackId("test-tracking-number"));

        orderProcessor.shipOrder(task, order);

        assertEquals("test-tracking-number", order.getDeliveryTrackingId());
        assertEquals(ORDER_SHIPPED, task.getStatus());
    }

    @Test
    void reportOrderNotShipped__should_update_reject_reason() {
        orderProcessor.reportOrderNotShipped(task, order);

        assertEquals("Unable to ship the order.", order.getRejectReason());
        assertEquals(REJECTED_NO_SHIPPING, task.getStatus());
    }

    @Test
    void finishOrderProcessing__should_update_order_status() {
        orderProcessor.finishOrderProcessing(task, order);

        assertEquals(OrderStatus.SHIPPED, order.getStatus());
        assertNull(task.getStatus());
    }

    @Test
    void cancelPayment__should_call_bank() {
        order.setPaymentTransactionId("test-id1");

        orderProcessor.cancelPayment(task, order);

        verify(bank).cancelPayment(eq(new Check("test-id1")));
        assertEquals(PAYMENT_CANCELLED, task.getStatus());
    }

    @Test
    void cancelPayment__should_skip_calling_bank() {
        order.setPaymentTransactionId(null);

        orderProcessor.cancelPayment(task, order);

        assertEquals(PAYMENT_CANCELLED, task.getStatus());
        verifyNoInteractions(bank);
    }

    @Test
    void cancelReservation__should_call_warehouse() {
        order.setReservationToken("test-token-7");

        orderProcessor.cancelReservation(task, order);

        verify(warehouse).cancelReservation(eq(new Reservation("test-token-7")));
        assertEquals(RESERVATION_CANCELLED, task.getStatus());
    }

    @Test
    void cancelReservation__should_skip_calling_warehouse() {
        order.setReservationToken(null);

        orderProcessor.cancelReservation(task, order);

        assertEquals(RESERVATION_CANCELLED, task.getStatus());
        verifyNoInteractions(warehouse);
    }

    @Test
    void rejectOrder__should_update_order_status() {
        orderProcessor.rejectOrder(task, order);

        assertEquals(OrderStatus.REJECTED, order.getStatus());
        assertNull(task.getStatus());
    }
}
