CREATE INDEX idx_comments_parent_created_at_id
 ON comments (parent_comment_id, created_at ASC, id ASC);
