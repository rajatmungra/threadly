CREATE TABLE posts (
    id UUID PRIMARY KEY,
    community_id UUID NOT NULL,
    author_id UUID NOT NULL,
    title VARCHAR(300) NOT NULL,
    content TEXT,
    url VARCHAR(2000),
    type VARCHAR(20) NOT NULL,
    score INTEGER NOT NULL DEFAULT 0,
    comment_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_posts_author_id ON posts (author_id);
CREATE INDEX idx_posts_community_created_at_id ON posts (community_id, created_at DESC, id DESC);
