package com.threadly.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApiGatewayRouteConfigurationTest {

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private RoutePredicateHandlerMapping routePredicateHandlerMapping;

    @Test
    void contextLoads() {
        assertThat(routeLocator).isNotNull();
        assertThat(routePredicateHandlerMapping).isNotNull();
    }

    @Test
    void shouldHaveExpectedRouteDefinitionsAndDestinations() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();

        assertThat(routes.stream().map(Route::getId).toList())
            .contains(
                "identity-auth",
                "identity-users",
                "community",
                "comment-post-comments",
                "comments",
                "posts",
                "notifications"
            );

        Route identityAuth = findRoute(routes, "identity-auth");
        assertThat(identityAuth.getUri().toString()).isEqualTo("http://localhost:8081");

        Route identityUsers = findRoute(routes, "identity-users");
        assertThat(identityUsers.getUri().toString()).isEqualTo("http://localhost:8081");

        Route community = findRoute(routes, "community");
        assertThat(community.getUri().toString()).isEqualTo("http://localhost:8082");

        Route commentPostComments = findRoute(routes, "comment-post-comments");
        assertThat(commentPostComments.getUri().toString()).isEqualTo("http://localhost:8084");
        assertThat(commentPostComments.getOrder()).isEqualTo(1);

        Route comments = findRoute(routes, "comments");
        assertThat(comments.getUri().toString()).isEqualTo("http://localhost:8084");

        Route posts = findRoute(routes, "posts");
        assertThat(posts.getUri().toString()).isEqualTo("http://localhost:8083");
        assertThat(posts.getOrder()).isEqualTo(2);

        Route notifications = findRoute(routes, "notifications");
        assertThat(notifications.getUri().toString()).isEqualTo("http://localhost:8085");
    }

    @Test
    void shouldSelectCommentServiceRouteForPostComments() {
        Route route = resolveRoute("/api/v1/posts/42/comments");
        assertThat(route).isNotNull();
        assertThat(route.getId()).isEqualTo("comment-post-comments");
        assertThat(route.getUri().toString()).isEqualTo("http://localhost:8084");
    }

    @Test
    void shouldSelectPostServiceRouteForPostDetail() {
        Route route = resolveRoute("/api/v1/posts/42");
        assertThat(route).isNotNull();
        assertThat(route.getId()).isEqualTo("posts");
        assertThat(route.getUri().toString()).isEqualTo("http://localhost:8083");
    }

    @Test
    void shouldSelectCommentServiceRouteForCommentVotes() {
        Route route = resolveRoute("/api/v1/comments/42/votes");
        assertThat(route).isNotNull();
        assertThat(route.getId()).isEqualTo("comments");
        assertThat(route.getUri().toString()).isEqualTo("http://localhost:8084");
    }

    @Test
    void shouldSelectNotificationServiceRouteForNotifications() {
        Route route = resolveRoute("/api/v1/notifications");
        assertThat(route).isNotNull();
        assertThat(route.getId()).isEqualTo("notifications");
        assertThat(route.getUri().toString()).isEqualTo("http://localhost:8085");
    }

    private Route resolveRoute(String path) {
        MockServerHttpRequest request = MockServerHttpRequest.get(path).build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        routePredicateHandlerMapping.getHandler(exchange).block();
        return exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
    }

    private Route findRoute(List<Route> routes, String id) {
        return routes.stream()
            .filter(r -> r.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Route not found: " + id));
    }
}
