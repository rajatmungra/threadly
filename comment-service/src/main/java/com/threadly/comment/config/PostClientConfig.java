package com.threadly.comment.config;

import com.threadly.comment.client.PostClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class PostClientConfig {

    @Bean
    public PostClient postClient(
        @Value("${services.post.url:http://localhost:8083}") String postServiceUrl,
        @Value("${services.post.connect-timeout-ms:3000}") int connectTimeoutMs,
        @Value("${services.post.read-timeout-ms:5000}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        RestClient restClient = RestClient.builder()
            .baseUrl(postServiceUrl)
            .requestFactory(requestFactory)
            .build();

        return new PostClient(restClient);
    }
}
