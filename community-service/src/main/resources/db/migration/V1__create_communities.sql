CREATE TABLE communities (
    id UUID PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_communities_name UNIQUE (name)
);

CREATE TABLE community_members (
    community_id UUID NOT NULL,
    user_id UUID NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_community_members PRIMARY KEY (community_id, user_id),
    CONSTRAINT fk_community_members_community_id FOREIGN KEY (community_id) REFERENCES communities (id) ON DELETE CASCADE
);
