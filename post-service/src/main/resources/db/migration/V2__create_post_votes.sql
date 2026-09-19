CREATE TABLE post_votes (
    post_id UUID NOT NULL,
    user_id UUID NOT NULL,
    value SMALLINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_post_votes PRIMARY KEY (post_id, user_id),
    CONSTRAINT fk_post_votes_post_id FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT chk_post_votes_value CHECK (value IN (-1, 1))
);
