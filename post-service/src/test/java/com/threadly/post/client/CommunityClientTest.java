package com.threadly.post.client;

import com.threadly.post.exception.CommunityNotFoundException;
import com.threadly.post.exception.CommunityServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
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

class CommunityClientTest {

    private MockRestServiceServer mockServer;
    private CommunityClient communityClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8082");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        communityClient = new CommunityClient(builder.build());
    }

    @Test
    void shouldSucceedWhenCommunityExists() {
        UUID communityId = UUID.randomUUID();
        String token = "valid-token";

        mockServer.expect(requestTo("http://localhost:8082/api/v1/communities/" + communityId))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer " + token))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatCode(() -> communityClient.verifyCommunityExists(communityId, token))
            .doesNotThrowAnyException();

        mockServer.verify();
    }

    @Test
    void shouldThrowCommunityNotFoundExceptionWhenDownstreamReturns404() {
        UUID communityId = UUID.randomUUID();
        String token = "valid-token";

        mockServer.expect(requestTo("http://localhost:8082/api/v1/communities/" + communityId))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer " + token))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> communityClient.verifyCommunityExists(communityId, token))
            .isInstanceOf(CommunityNotFoundException.class)
            .hasMessageContaining(communityId.toString());

        mockServer.verify();
    }

    @Test
    void shouldThrowCommunityServiceUnavailableExceptionWhenDownstreamReturns500() {
        UUID communityId = UUID.randomUUID();
        String token = "valid-token";

        mockServer.expect(requestTo("http://localhost:8082/api/v1/communities/" + communityId))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer " + token))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> communityClient.verifyCommunityExists(communityId, token))
            .isInstanceOf(CommunityServiceUnavailableException.class)
            .hasMessageContaining("downstream returned 500");

        mockServer.verify();
    }

    @Test
    void shouldThrowCommunityServiceUnavailableExceptionWhenDownstreamReturns503() {
        UUID communityId = UUID.randomUUID();
        String token = "valid-token";

        mockServer.expect(requestTo("http://localhost:8082/api/v1/communities/" + communityId))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer " + token))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> communityClient.verifyCommunityExists(communityId, token))
            .isInstanceOf(CommunityServiceUnavailableException.class)
            .hasMessageContaining("downstream returned 503");

        mockServer.verify();
    }

    @Test
    void shouldThrowCommunityServiceUnavailableExceptionOnConnectionOrTimeoutError() {
        UUID communityId = UUID.randomUUID();
        String token = "valid-token";

        mockServer.expect(requestTo("http://localhost:8082/api/v1/communities/" + communityId))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer " + token))
            .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> communityClient.verifyCommunityExists(communityId, token))
            .isInstanceOf(CommunityServiceUnavailableException.class)
            .hasMessageContaining("Community service is unavailable");

        mockServer.verify();
    }
}
