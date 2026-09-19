package com.threadly.post.config;

import com.threadly.post.client.CommunityClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class CommunityClientConfig {

    @Bean
    public CommunityClient communityClient(
        @Value("${services.community.url:http://localhost:8082}") String communityServiceUrl,
        @Value("${services.community.connect-timeout-ms:3000}") int connectTimeoutMs,
        @Value("${services.community.read-timeout-ms:5000}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        RestClient restClient = RestClient.builder()
            .baseUrl(communityServiceUrl)
            .requestFactory(requestFactory)
            .build();

        return new CommunityClient(restClient);
    }
}
