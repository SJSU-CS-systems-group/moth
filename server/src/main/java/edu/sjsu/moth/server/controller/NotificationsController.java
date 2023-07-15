package edu.sjsu.moth.server.controller;

import edu.sjsu.moth.server.db.TokenRepository;
import edu.sjsu.moth.server.util.MothConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.List;

//sources: https://docs.joinmastodon.org/methods/notifications/#get
//https://docs.joinmastodon.org/entities/Notification/
@RestController
public class NotificationsController {

    private final WebClient webClient;
    private final TokenRepository tokenRepository;

    public NotificationsController(WebClient.Builder webClientBuilder, TokenRepository tokenRepository) {
        this.webClient = webClientBuilder.baseUrl("https://" + MothConfiguration.mothConfiguration.getServerName()).build();
        this.tokenRepository = tokenRepository;
    }

    @GetMapping("/api/v1/notifications")
    public Mono<ResponseEntity<?>> getAllNotifications(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            Principal principal,
            @RequestParam(required = false) String max_id,
            @RequestParam(required = false) String since_id,
            @RequestParam(required = false) String min_id,
            @RequestParam(defaultValue = "15") int limit,
            @RequestParam(required = false) String[] types,
            @RequestParam(required = false) String[] exclude_types,
            @RequestParam(required = false) String account_id
    ) {
        String username = principal.getName();

        //extract token from authorization header
        String token = extractToken(authorization);

        //check if token is same as the user's from repo
        return tokenRepository.findItemByUser(username)
                .flatMap(tokenEntity -> {
                    String storedToken = tokenEntity.getToken();

                    if (storedToken.equals(token)) {
                        String apiUrl = "/api/v1/notifications";

                        //below are query params to filter the notifications
                        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath(apiUrl)
                                .queryParam("max_id", max_id)
                                .queryParam("since_id", since_id)
                                .queryParam("min_id", min_id)
                                .queryParam("limit", limit)
                                .queryParam("types", types)
                                .queryParam("exclude_types", exclude_types)
                                .queryParam("account_id", account_id);

                        return webClient.get()
                                .uri(uriBuilder.build().toUri())
                                .header(HttpHeaders.AUTHORIZATION, authorization)
                                .retrieve()
                                .toEntityList(Notification.class)
                                .flatMap(response -> {
                                    HttpHeaders headers = response.getHeaders();
                                    HttpStatus statusCode = (HttpStatus) response.getStatusCode();
                                    List<Notification> notifications = response.getBody();

                                    if (statusCode.is2xxSuccessful() && notifications != null) {
                                        //add link headers for pagination!! so users can navigate thru multiple pages of notifs
                                        headers.add(HttpHeaders.LINK, createLinkHeader(headers, apiUrl, limit, notifications.size(), max_id, since_id));

                                        return Mono.just(ResponseEntity.ok().headers(headers).body(notifications));
                                    } else {
                                        return Mono.just(ResponseEntity.status(statusCode).headers(headers).build());
                                    }
                                });
                    } else {
                        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
                    }
                })
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    private String extractToken(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }
        return null;
    }

    private String createLinkHeader(HttpHeaders headers, String apiUrl, int limit, int resultCount, String maxId, String sinceId) {
        StringBuilder linkHeader = new StringBuilder();

        String baseLink = "<" + apiUrl + "?limit=" + limit;

        if (maxId != null) {
            linkHeader.append(baseLink).append("&max_id=").append(maxId).append(">; rel=\"next\", ");
        }

        if (sinceId != null) {
            linkHeader.append(baseLink).append("&since_id=").append(sinceId).append(">; rel=\"prev\", ");
        }

        linkHeader.append(baseLink).append(">; rel=\"first\"");

        //add link to the next page if the current page is full
        if (resultCount == limit) {
            String nextPageLink = baseLink + "&max_id=" + (maxId != null ? maxId : "") + ">; rel=\"next\"";
            linkHeader.append(", ").append(nextPageLink);
        }

        return linkHeader.toString();
    }

    public record Notification(
            String id,
            String type,
            String created_at,
            Account account,
            Status status,
            Report report
    ) {
        public record Account(
                String id,
                String username,
                String acct
        ) {}

        public record Status(
                String id,
                String created_at,
                String in_reply_to_id,
                String in_reply_to_account_id
        ) {}
        public record Report(
        ) {}
    }
}
