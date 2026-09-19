package com.threadly.post.controller;

import com.threadly.post.dto.request.CreatePostRequest;
import com.threadly.post.dto.response.PagedResponse;
import com.threadly.post.dto.response.PostResponse;
import com.threadly.post.service.PostService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse createPost(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreatePostRequest request
    ) {
        UUID authorId = parseAuthorId(jwt);
        String token = jwt.getTokenValue();
        return postService.createPost(request, authorId, token);
    }

    @GetMapping("/{postId}")
    public PostResponse getPost(@PathVariable UUID postId) {
        return postService.getPost(postId);
    }

    @GetMapping
    public PagedResponse<PostResponse> getPosts(
        @RequestParam UUID communityId,
        @RequestParam(defaultValue = "new") String sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return postService.getPosts(communityId, sort, page, size);
    }

    private UUID parseAuthorId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new InvalidBearerTokenException("Missing token subject");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ex) {
            throw new InvalidBearerTokenException("Invalid token subject format");
        }
    }
}
