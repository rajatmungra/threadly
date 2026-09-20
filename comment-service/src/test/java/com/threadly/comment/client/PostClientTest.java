package com.threadly.comment.client;

import com.threadly.comment.exception.PostNotFoundException;
import com.threadly.comment.exception.PostServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PostClientTest {

    private MockRestServiceServer mockServer;
    private PostClient postClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8083");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        postClient = new PostClient(builder.build());
    }

    @Test
    void shouldSucceedWhenPostExists() {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        String token = "valid-jwt-token";

        mockServer.expect(requestTo("http://localhost:8083/api/v1/posts/" + postId))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer " + token))
            .andRespond(withSuccess(
                "{\"id\":\"" + postId + "\",\"authorId\":\"" + authorId + "\"}",
                MediaType.APPLICATION_JSON
            ));

        PostDto post = postClient.verifyPostExists(postId, token);
        org.assertj.core.api.Assertions.assertThat(post).isNotNull();
        org.assertj.core.api.Assertions.assertThat(post.id()).isEqualTo(postId);
        org.assertj.core.api.Assertions.assertThat(post.authorId()).isEqualTo(authorId);

        mockServer.verify();
    }

    @Test
    void shouldThrowPostNotFoundExceptionWhenDownstreamReturns404() {
        UUID postId = UUID.randomUUID();
        String token = "valid-jwt-token";

        mockServer.expect(requestTo("http://localhost:8083/api/v1/posts/" + postId))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer " + token))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> postClient.verifyPostExists(postId, token))
            .isInstanceOf(PostNotFoundException.class)
            .hasMessageContaining(postId.toString());

        mockServer.verify();
    }

    @Test
    void shouldThrowPostServiceUnavailableExceptionWhenDownstreamReturns500() {
        UUID postId = UUID.randomUUID();
        String token = "valid-jwt-token";

        mockServer.expect(requestTo("http://localhost:8083/api/v1/posts/" + postId))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer " + token))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> postClient.verifyPostExists(postId, token))
            .isInstanceOf(PostServiceUnavailableException.class)
            .hasMessageContaining("downstream returned 500");

        mockServer.verify();
    }

    @Test
    void shouldThrowPostServiceUnavailableExceptionWhenDownstreamReturnsUnexpected401() {
        UUID postId = UUID.randomUUID();
        String token = "valid-jwt-token";

        mockServer.expect(requestTo("http://localhost:8083/api/v1/posts/" + postId))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer " + token))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> postClient.verifyPostExists(postId, token))
            .isInstanceOf(PostServiceUnavailableException.class)
            .hasMessageContaining("unexpected status: 401");

        mockServer.verify();
    }

    @Test
    void shouldThrowPostServiceUnavailableExceptionOnTimeout() {
        UUID postId = UUID.randomUUID();
        String token = "valid-jwt-token";

        mockServer.expect(requestTo("http://localhost:8083/api/v1/posts/" + postId))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer " + token))
            .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> postClient.verifyPostExists(postId, token))
            .isInstanceOf(PostServiceUnavailableException.class)
            .hasMessageContaining("Post service is unavailable");

        mockServer.verify();
    }
}
