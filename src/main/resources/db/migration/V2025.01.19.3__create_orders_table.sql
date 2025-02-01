CREATE TABLE orders
(
    id UUID PRIMARY KEY,
    task_id BIGINT,
    status VARCHAR,
    customer VARCHAR,
    items VARCHAR,
    reject_reason VARCHAR,
    reservation_token VARCHAR,
    payment_transaction_id VARCHAR,
    delivery_tracking_id VARCHAR
);

CREATE INDEX ndx_orders_task_id ON orders (task_id);

