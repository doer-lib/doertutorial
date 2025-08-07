package com.doertutorial;

import com.doer.DoerLoader;
import com.doer.DoerUnloader;
import com.doer.Task;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.*;
import jakarta.transaction.Transactional;

import javax.sql.DataSource;
import java.io.StringReader;
import java.sql.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@ApplicationScoped
@Transactional
public class OrderDao {
    @Inject
    DataSource ds;

    public void insertOrder(Order order) throws SQLException {
        String sql = "INSERT INTO orders (id, created, modified, task_id, status, json_data) " +
                "VALUES (?, now(), now(), ?, ?, ?::JSONB) " +
                "RETURNING *";
        try (Connection con = ds.getConnection(); PreparedStatement pst = con.prepareStatement(sql)) {
            UUID id = order.getId() != null ? order.getId() : UUID.randomUUID();
            pst.setObject(1, id);
            if (order.getTaskId() != null) {
                pst.setLong(2, order.getTaskId());
            } else {
                pst.setNull(2, Types.BIGINT);
            }
            pst.setString(3, order.getStatus() == null ? null : order.getStatus().name());
            pst.setString(4, createJsonData(order));
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    order.assignFieldsFrom(readOrder(rs));
                } else {
                    throw new IllegalStateException("INSERT ... RETURNING * call failed");
                }
            }
        }
    }

    public void updateOrder(Order order) throws SQLException {
        String sql = "UPDATE orders SET modified = now(), task_id = ?, status = ?, json_data = ?::JSONB " +
                "WHERE id = ? " +
                "RETURNING *";
        try (Connection con = ds.getConnection(); PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setLong(1, order.getTaskId());
            pst.setString(2, order.getStatus() == null ? null : order.getStatus().name());
            pst.setString(3, createJsonData(order));
            pst.setObject(4, order.getId());
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    order.assignFieldsFrom(readOrder(rs));
                } else {
                    throw new IllegalStateException("Order not found in database");
                }
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

    public JsonObject loadLastFailureExtraJson(long taskId) throws SQLException {
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
                    String jsonString = rs.getString("extra_json");
                    return jsonString == null ? null : Json.createReader(new StringReader(jsonString)).readObject();
                }
            }
        }
        return null;
    }

    static Order readOrder(ResultSet rs) throws SQLException {
        Order order = new Order();
        order.setId(rs.getObject("id", UUID.class));
        order.setCreated(odtToInstant(rs.getObject("created", OffsetDateTime.class)));
        order.setModified(odtToInstant(rs.getObject("modified", OffsetDateTime.class)));
        order.setTaskId(readNullableLong(rs, "task_id"));
        order.setStatus(safeReadOrderStatus(rs, "status"));
        updateOrderFromJsonData(order, rs.getString("json_data"));
        return order;
    }

    static Long readNullableLong(ResultSet rs, String column) throws SQLException {
        long result = rs.getLong(column);
        return rs.wasNull() ? null : result;
    }

    static OrderStatus safeReadOrderStatus(ResultSet rs, String column) throws SQLException {
        String string = rs.getString(column);
        try {
            return OrderStatus.valueOf(string);
        } catch (IllegalArgumentException e) {
            Log.warn("Failed to parse OrderStatus " + string + ". Setting null instead.");
            return null;
        }
    }

    static Instant odtToInstant(OffsetDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant();
    }

    static String createJsonData(Order order) {
        if (order == null) {
            return null;
        }
        JsonObjectBuilder b = Json.createObjectBuilder();
        if (order.getCustomer() != null) {
            b.add("customer", order.getCustomer());
        }
        if (order.getItems() != null) {
            b.add("items", order.getItems());
        }
        if (order.getRejectReason() != null) {
            b.add("reject_reason", order.getRejectReason());
        }
        if (order.getReservationToken() != null) {
            b.add("reservation_token", order.getReservationToken());
        }
        if (order.getPaymentTransactionId() != null) {
            b.add("payment_transaction_id", order.getPaymentTransactionId());
        }
        if (order.getDeliveryTrackingId() != null) {
            b.add("delivery_tracking_id", order.getDeliveryTrackingId());
        }
        if (order.getFailureDetails() != null) {
            b.add("failure_details", order.getFailureDetails());
        }
        return b.build().toString();
    }

    static void updateOrderFromJsonData(Order order, String jsonData) {
        if (jsonData == null) {
            return;
        }
        JsonObject json = Json.createReader(new StringReader(jsonData)).readObject();
        order.setCustomer(json.getString("customer", null));
        order.setItems(json.getString("items", null));
        order.setRejectReason(json.getString("reject_reason", null));
        order.setReservationToken(json.getString("reservation_token", null));
        order.setPaymentTransactionId(json.getString("payment_transaction_id", null));
        order.setDeliveryTrackingId(json.getString("delivery_tracking_id", null));
        order.setFailureDetails(json.getJsonObject("failure_details"));
    }
}

