CREATE TABLE IF NOT EXISTS processed_kafka_events (
    event_id BIGINT PRIMARY KEY,
    event_type VARCHAR(40) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
