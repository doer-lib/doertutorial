CREATE TABLE orders
(
    id UUID PRIMARY KEY,
    created TIMESTAMP WITH TIME ZONE,
    modified TIMESTAMP WITH TIME ZONE,
    task_id BIGINT,
    status VARCHAR,
    json_data JSONB
);

CREATE INDEX ndx_orders_task_id ON orders (task_id);
CREATE INDEX ndx_orders_json_data ON orders USING gin(json_data);

