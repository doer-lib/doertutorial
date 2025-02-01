package com.doertutorial;

import com.doer.AcceptStatus;
import com.doer.DoerService;
import com.doer.Task;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.sql.SQLException;

@ApplicationScoped
public class OrderProcessor {
    public static final String NEW_ORDER_CREATED = "New order created";

    @Inject
    DoerService doerService;
    @Inject
    OrderDao orderDao;

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
    public void startOrderProcessing(Task task) {
        task.setStatus(null);
    }
}
