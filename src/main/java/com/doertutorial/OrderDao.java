package com.doertutorial;

import com.doer.DoerLoader;
import com.doer.DoerUnloader;
import com.doer.Task;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@ApplicationScoped
@Transactional
public class OrderDao {
    @Inject
    DataSource ds;

    public void insertOrder(Order order) throws SQLException {
        String sql = "INSERT INTO orders (id, task_id, status, customer, items, reject_reason, " +
                     "reservation_token, payment_transaction_id, delivery_tracking_id) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = ds.getConnection(); PreparedStatement pst = con.prepareStatement(sql)) {
            UUID id = order.getId() != null ? order.getId() : UUID.randomUUID();
            pst.setObject(1, id);
            pst.setLong(2, order.getTaskId());
            pst.setString(3, order.getStatus() == null ? null : order.getStatus().name());
            pst.setString(4, order.getCustomer());
            pst.setString(5, order.getItems());
            pst.setString(6, order.getRejectReason());
            pst.setString(7, order.getReservationToken());
            pst.setString(8, order.getPaymentTransactionId());
            pst.setString(9, order.getDeliveryTrackingId());
            pst.executeUpdate();
            order.setId(id);
        }
    }

    public void updateOrder(Order order) throws SQLException {
        String sql = "UPDATE orders SET task_id = ?, status = ?, customer = ?, items = ?, reject_reason = ?, " +
                     "reservation_token = ?, payment_transaction_id = ?, delivery_tracking_id = ? " +
                     "WHERE id = ?";
        try (Connection con = ds.getConnection(); PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setLong(1, order.getTaskId());
            pst.setString(2, order.getStatus() == null ? null : order.getStatus().name());
            pst.setString(3, order.getCustomer());
            pst.setString(4, order.getItems());
            pst.setString(5, order.getRejectReason());
            pst.setString(6, order.getReservationToken());
            pst.setString(7, order.getPaymentTransactionId());
            pst.setString(8, order.getDeliveryTrackingId());
            pst.setObject(9, order.getId());
            int rowsAffected = pst.executeUpdate();
            if (rowsAffected != 1) {
                throw new IllegalStateException("Expected updated 1 row, but was " + rowsAffected);
            }
        }
    }

    public Order findOrderById(UUID id) throws SQLException {
        String sql = "SELECT * FROM orders WHERE id = ?";
        try (Connection con = ds.getConnection(); PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setObject(1, id);
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    return readOrder(rs);
                }
                return null;
            }
        }
    }

    public Order findOrderByTaskId(long taskId) throws SQLException {
        String sql = "SELECT * FROM orders WHERE task_id = ? ORDER BY id LIMIT 1";
        try (Connection con = ds.getConnection(); PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setObject(1, taskId);
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    return readOrder(rs);
                }
                return null;
            }
        }
    }

    @DoerLoader
    public Order loadOrderForTask(Task task) throws SQLException {
        return findOrderByTaskId(task.getId());
    }

    @DoerUnloader
    public void saveOrder(Task task, Order order) throws SQLException {
        updateOrder(order);
    }

    public String loadLastFailureExtraJson(long taskId) throws SQLException {
        String sql = """
                SELECT *
                FROM task_logs
                WHERE exception_type IS NOT NULL
                  AND task_id = ?
                ORDER BY created DESC
                LIMIT 1""";
        try (Connection con = ds.getConnection(); PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setLong(1, taskId);
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("extra_json");
                }
            }
        }
        return null;
    }

    private Order readOrder(ResultSet rs) throws SQLException {
        Order order = new Order();
        order.setId(rs.getObject("id", UUID.class));
        order.setTaskId(readNullableLong(rs, "task_id"));
        order.setStatus(safeReadOrderStatus(rs, "status"));
        order.setCustomer(rs.getString("customer"));
        order.setItems(rs.getString("items"));
        order.setRejectReason(rs.getString("reject_reason"));
        order.setReservationToken(rs.getString("reservation_token"));
        order.setPaymentTransactionId(rs.getString("payment_transaction_id"));
        order.setDeliveryTrackingId(rs.getString("delivery_tracking_id"));
        return order;
    }

    private Long readNullableLong(ResultSet rs, String column) throws SQLException {
        long result = rs.getLong(column);
        return rs.wasNull() ? null : result;
    }

    private OrderStatus safeReadOrderStatus(ResultSet rs, String column) throws SQLException {
        String string = rs.getString(column);
        try {
            return OrderStatus.valueOf(string);
        } catch (IllegalArgumentException e) {
            Log.warn("Failed to parse OrderStatus " + string + ". Setting null instead.");
            return null;
        }
    }
}
