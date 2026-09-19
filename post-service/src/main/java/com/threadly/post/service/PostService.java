package com.threadly.post.service;

import com.threadly.post.cache.PostFeedCache;
import com.threadly.post.client.CommunityClient;
import com.threadly.post.dto.request.CreatePostRequest;
import com.threadly.post.dto.response.PagedResponse;
import com.threadly.post.dto.response.PostResponse;
import com.threadly.post.entity.Post;
import com.threadly.post.entity.PostType;
import com.threadly.post.exception.InvalidPaginationException;
import com.threadly.post.exception.InvalidSortException;
import com.threadly.post.exception.PostNotFoundException;
import com.threadly.post.repository.PostRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final CommunityClient communityClient;
    private final PostFeedCache postFeedCache;

    public PostService(PostRepository postRepository, CommunityClient communityClient, PostFeedCache postFeedCache) {
        this.postRepository = postRepository;
        this.communityClient = communityClient;
        this.postFeedCache = postFeedCache;
    }

    public PostResponse createPost(CreatePostRequest request, UUID authorId, String token) {
        communityClient.verifyCommunityExists(request.getCommunityId(), token);

        Post post = new Post();
        post.setCommunityId(request.getCommunityId());
        post.setAuthorId(authorId);
        post.setTitle(request.getTitle().trim());
        if (request.getType() == PostType.TEXT) {
            post.setContent(request.getContent().trim());
            post.setUrl(null);
        } else {
            post.setContent(request.getContent() != null ? request.getContent().trim() : null);
            post.setUrl(request.getUrl().trim());
        }
        post.setType(request.getType());
        post.setScore(0);
        post.setCommentCount(0);
        Instant now = Instant.now();
        post.setCreatedAt(now);
        post.setUpdatedAt(now);

        Post savedPost = postRepository.save(post);
        postFeedCache.evict(savedPost.getCommunityId());
        return PostResponse.fromEntity(savedPost);
    }

    @Transactional(readOnly = true)
    public PostResponse getPost(UUID postId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new PostNotFoundException("Post not found with ID: " + postId));
        return PostResponse.fromEntity(post);
    }

    @Transactional(readOnly = true)
    public PagedResponse<PostResponse> getPosts(UUID communityId, String sort, int page, int size) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        if (page < 0) {
            fieldErrors.put("page", "Page index must not be less than zero");
        }
        if (size < 1) {
            fieldErrors.put("size", "Page size must be at least 1");
        } else if (size > 100) {
            fieldErrors.put("size", "Page size must not exceed 100");
        }
        if (!fieldErrors.isEmpty()) {
            throw new InvalidPaginationException("Invalid pagination parameters", fieldErrors);
        }

        String normalizedSort = sort != null ? sort.trim().toLowerCase(Locale.ROOT) : null;
        if (!"new".equals(normalizedSort) && !"top".equals(normalizedSort)) {
            throw new InvalidSortException("Unsupported sort: " + sort + ". Only 'new' and 'top' are supported");
        }

        boolean cacheable = (page == 0 && size == 20);
        if (cacheable) {
            Optional<PagedResponse<PostResponse>> cached = postFeedCache.get(communityId, normalizedSort);
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        Sort sortSpec;
        if ("top".equals(normalizedSort)) {
            sortSpec = Sort.by(Sort.Order.desc("score"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        } else {
            sortSpec = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        }
        Pageable pageable = PageRequest.of(page, size, sortSpec);
        Page<Post> postPage = postRepository.findByCommunityId(communityId, pageable);

        List<PostResponse> content = postPage.getContent().stream()
            .map(PostResponse::fromEntity)
            .toList();

        PagedResponse<PostResponse> response = new PagedResponse<>(
            content,
            postPage.getNumber(),
            postPage.getSize(),
            postPage.getTotalElements(),
            postPage.getTotalPages(),
            postPage.isLast()
        );

        if (cacheable) {
            postFeedCache.put(communityId, normalizedSort, response);
        }

        return response;
    }
}
