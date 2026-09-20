CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    source_event_id UUID NOT NULL,
    user_id UUID NOT NULL,
    type VARCHAR(30) NOT NULL,
    actor_user_id UUID NOT NULL,
    post_id UUID NOT NULL,
    comment_id UUID NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_notifications_source_event_id UNIQUE (source_event_id)
);

CREATE INDEX idx_notifications_user_created_id ON notifications (user_id, created_at DESC, id DESC);
