CREATE TABLE comment_votes (
    comment_id UUID NOT NULL,
    user_id UUID NOT NULL,
    value SMALLINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_comment_votes PRIMARY KEY (comment_id, user_id),
    CONSTRAINT fk_comment_votes_comment_id FOREIGN KEY (comment_id) REFERENCES comments (id) ON DELETE CASCADE,
    CONSTRAINT chk_comment_votes_value CHECK (value IN (-1, 1))
);
