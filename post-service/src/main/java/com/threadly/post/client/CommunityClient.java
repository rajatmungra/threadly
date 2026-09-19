package com.threadly.post.client;

import com.threadly.post.exception.CommunityNotFoundException;
import com.threadly.post.exception.CommunityServiceUnavailableException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

public class CommunityClient {

    private final RestClient restClient;

    public CommunityClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public void verifyCommunityExists(UUID communityId, String bearerToken) {
        try {
            restClient.get()
                .uri("/api/v1/communities/{communityId}", communityId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .retrieve()
                .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new CommunityNotFoundException("Community not found with id: " + communityId);
            }
            throw ex;
        } catch (HttpServerErrorException ex) {
            throw new CommunityServiceUnavailableException("Community service is unavailable: downstream returned " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            throw new CommunityServiceUnavailableException("Community service is unavailable", ex);
        }
    }
}
