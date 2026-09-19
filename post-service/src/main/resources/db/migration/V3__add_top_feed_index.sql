CREATE INDEX idx_posts_community_score_created_at_id
ON posts (community_id, score DESC, created_at DESC, id DESC);
