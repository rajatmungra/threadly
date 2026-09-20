package com.threadly.comment.client;

import com.threadly.comment.exception.PostNotFoundException;
import com.threadly.comment.exception.PostServiceUnavailableException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

public class PostClient {

    private final RestClient restClient;

    public PostClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public PostDto verifyPostExists(UUID postId, String bearerToken) {
        try {
            PostDto post = restClient.get()
                .uri("/api/v1/posts/{postId}", postId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .retrieve()
                .body(PostDto.class);

            if (post == null) {
                throw new PostNotFoundException("Post not found with id: " + postId);
            }
            return post;
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new PostNotFoundException("Post not found with id: " + postId);
            }
            throw new PostServiceUnavailableException("Post service returned unexpected status: " + ex.getStatusCode(), ex);
        } catch (HttpServerErrorException ex) {
            throw new PostServiceUnavailableException("Post service is unavailable: downstream returned " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            throw new PostServiceUnavailableException("Post service is unavailable", ex);
        }
    }
}
