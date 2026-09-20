package com.threadly.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "IDENTITY_SERVICE_URL=http://custom-identity:9999"
})
class ApiGatewayEnvironmentOverrideTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void shouldOverrideIdentityServiceUrlViaEnvironmentProperty() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();

        Route identityAuth = routes.stream()
            .filter(r -> "identity-auth".equals(r.getId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Route identity-auth not found"));

        assertThat(identityAuth.getUri().toString()).isEqualTo("http://custom-identity:9999");
    }
}
