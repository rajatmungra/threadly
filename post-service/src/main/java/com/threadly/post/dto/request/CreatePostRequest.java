package com.threadly.post.dto.request;

import com.threadly.post.entity.PostType;
import com.threadly.post.validation.ValidPost;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@ValidPost
public class CreatePostRequest {

    @NotNull(message = "Community ID is required")
    private UUID communityId;

    @NotBlank(message = "Title is required")
    @Size(min = 3, max = 300, message = "Title must be between 3 and 300 characters")
    private String title;

    @NotNull(message = "Post type is required")
    private PostType type;

    @Size(max = 40000, message = "Content cannot exceed 40000 characters")
    private String content;

    @Size(max = 2000, message = "URL cannot exceed 2000 characters")
    private String url;

    public CreatePostRequest() {
    }

    public CreatePostRequest(UUID communityId, String title, PostType type, String content, String url) {
        this.communityId = communityId;
        this.title = title;
        this.type = type;
        this.content = content;
        this.url = url;
    }

    public UUID getCommunityId() {
        return communityId;
    }

    public void setCommunityId(UUID communityId) {
        this.communityId = communityId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public PostType getType() {
        return type;
    }

    public void setType(PostType type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
