package com.aitalent.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.lang.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    @org.springframework.context.annotation.Bean
    WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @org.springframework.context.annotation.Bean
    org.springframework.web.reactive.function.server.RouterFunction<org.springframework.web.reactive.function.server.ServerResponse> routes(
            WebClient.Builder webClientBuilder,
            @Value("${services.user-url}") @NonNull String userUrl,
            @Value("${services.candidate-url}") @NonNull String candidateUrl,
            @Value("${services.notification-url}") @NonNull String notificationUrl
    ) {
        WebClient userClient = webClientBuilder.baseUrl(userUrl).build();
        WebClient candidateClient = webClientBuilder.baseUrl(candidateUrl).build();
        WebClient notificationClient = webClientBuilder.baseUrl(notificationUrl).build();

        return org.springframework.web.reactive.function.server.RouterFunctions.route()
            .OPTIONS("/api/**", req -> org.springframework.web.reactive.function.server.ServerResponse.ok()
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS")
                .header("Access-Control-Allow-Headers", "Authorization, X-Tenant-Id, Content-Type, Accept")
                .header("Access-Control-Max-Age", "3600")
                .bodyValue(""))
            .route(org.springframework.web.reactive.function.server.RequestPredicates.path("/api/auth/**"), req -> ProxyHandler.forward(req, userClient))
            .route(org.springframework.web.reactive.function.server.RequestPredicates.path("/api/candidates/**"), req -> ProxyHandler.forward(req, candidateClient))
            .route(org.springframework.web.reactive.function.server.RequestPredicates.path("/api/notifications/**"), req -> ProxyHandler.forward(req, notificationClient))
                .GET("/health", req -> org.springframework.web.reactive.function.server.ServerResponse.ok().bodyValue("ok"))
                .build();
    }

        @org.springframework.context.annotation.Bean
        CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
        }
}

class ProxyHandler {

    static Mono<org.springframework.web.reactive.function.server.ServerResponse> forward(
            org.springframework.web.reactive.function.server.ServerRequest request,
            WebClient client
    ) {
        WebClient.RequestBodySpec requestSpec = client.method(request.method())
                .uri(request.uri().getRawPath() + (request.uri().getRawQuery() == null ? "" : "?" + request.uri().getRawQuery()))
                .headers(headers -> {
                    request.headers().asHttpHeaders().forEach((name, values) -> {
                        if (!HttpHeaders.HOST.equalsIgnoreCase(name)
                                && !HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name)) {
                            headers.put(name, values);
                        }
                    });
                });

        WebClient.RequestHeadersSpec<?> upstreamRequest;
        if (HttpMethod.GET.equals(request.method()) || HttpMethod.OPTIONS.equals(request.method())) {
            upstreamRequest = requestSpec;
        } else {
            Flux<DataBuffer> bodyFlux = request.exchange().getRequest().getBody();
            upstreamRequest = requestSpec.body(BodyInserters.fromDataBuffers(bodyFlux));
        }

        return upstreamRequest.exchangeToMono(clientResponse -> {
            return clientResponse.bodyToMono(byte[].class)
                    .defaultIfEmpty(new byte[0])
                    .flatMap(responseBody -> {
                        org.springframework.web.reactive.function.server.ServerResponse.BodyBuilder responseBuilder =
                                org.springframework.web.reactive.function.server.ServerResponse.status(clientResponse.statusCode())
                                .header("Access-Control-Allow-Origin", "*")
                                .header("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS")
                                .header("Access-Control-Allow-Headers", "Authorization, X-Tenant-Id, Content-Type, Accept")
                                .header("Access-Control-Max-Age", "3600");

                        clientResponse.headers().asHttpHeaders().forEach((name, values) -> {
                            if (!HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(name)
                                    && !HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name)
                                    && !HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN.equalsIgnoreCase(name)
                                    && !HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS.equalsIgnoreCase(name)
                                    && !HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS.equalsIgnoreCase(name)
                                    && !HttpHeaders.ACCESS_CONTROL_MAX_AGE.equalsIgnoreCase(name)) {
                                responseBuilder.header(Objects.requireNonNull(name), Objects.requireNonNull(values.toArray(new String[0])));
                            }
                        });

                        return responseBuilder.bodyValue(Objects.requireNonNull(responseBody));
                    });
        });
    }
}

@Component
class JwtTenantFilter implements WebFilter {

    private final SecretKey key;

    JwtTenantFilter(@Value("${app.jwt.secret}") String secret) {
        String normalized = secret;
        if (normalized.length() < 32) {
            normalized = normalized + "-secure-key-padding-2026";
        }
        this.key = Keys.hmacShaKeyFor(normalized.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return Objects.requireNonNull(chain.filter(exchange));
        }

        // Candidate apply endpoint remains public; all other candidate endpoints require recruiter JWT.
        if (path.startsWith("/api/candidates") && !path.equals("/api/candidates/apply")) {
            Claims claims = parseBearer(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            String tenantHeader = request.getHeaders().getFirst("X-Tenant-Id");
            String tenantClaim = claims.get("tenantId", String.class);
            String role = claims.get("role", String.class);

            if (tenantHeader == null || !Objects.equals(tenantHeader, tenantClaim)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant mismatch");
            }
            if (!"RECRUITER".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Recruiter role required");
            }
        }

        return Objects.requireNonNull(chain.filter(exchange));
    }

    private Claims parseBearer(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing bearer token");
        }
        String token = authHeader.substring("Bearer ".length());
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }
    }
}
