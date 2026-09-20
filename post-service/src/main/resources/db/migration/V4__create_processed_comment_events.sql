CREATE TABLE processed_comment_events (
    event_id UUID PRIMARY KEY,
    post_id UUID NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_processed_comment_events_post_id ON processed_comment_events (post_id);
