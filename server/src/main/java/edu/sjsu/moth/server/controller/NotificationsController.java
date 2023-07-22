package edu.sjsu.moth.server.controller;

import edu.sjsu.moth.generated.Notification;
import edu.sjsu.moth.server.db.TokenRepository;
import edu.sjsu.moth.server.util.MothConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

@RestController
public class NotificationsController {

    private final TokenRepository tokenRepository;
    private final ReactiveMongoTemplate reactiveMongoTemplate;

    @Autowired
    public NotificationsController(
            WebClient.Builder webClientBuilder,
            TokenRepository tokenRepository,
            ReactiveMongoTemplate reactiveMongoTemplate
    ) {
        WebClient webClient = webClientBuilder.baseUrl("https://" + MothConfiguration.mothConfiguration.getServerName())
                .build();
        this.tokenRepository = tokenRepository;
        this.reactiveMongoTemplate = reactiveMongoTemplate;
    }

    //NotificationWithUser class that extends Notification to include user info
    public static class NotificationWithUser extends Notification {
    }


    @GetMapping("/api/v1/notifications")
    public Mono<ResponseEntity<? extends List<? extends Object>>> getAllNotifications(
            Principal principal,
            @RequestParam(required = false) String max_id,
            @RequestParam(required = false) String since_id,
            @RequestParam(required = false) String min_id,
            @RequestParam(defaultValue = "15") int limit,
            @RequestParam(required = false) String[] types,
            @RequestParam(required = false) String[] exclude_types,
            @RequestParam(required = false) String account_id
    ) {
        //if user is not authenticated
        if (principal == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }

        String username = principal.getName();

        return tokenRepository.findItemByUser(username)
                .flatMap(tokenEntity -> {

                    int effectiveLimit = Math.min(limit, 30);
                    Pageable pageable = PageRequest.of(0, effectiveLimit);

                    Query query = new Query();
                    query.with(pageable);

                    //criteria to filter notifs for the authenticated user
                    query.addCriteria(Criteria.where("account.id").is(account_id));

                    if (max_id != null) {
                        query.addCriteria(Criteria.where("id").lt(max_id));
                    }

                    if (since_id != null) {
                        query.addCriteria(Criteria.where("id").gt(since_id));
                    }

                    if (min_id != null) {
                        query.addCriteria(Criteria.where("id").gt(min_id));
                    }

                    //execute query with reactiveMongoTemplate and NotificationWithUser class
                    return reactiveMongoTemplate.find(query, NotificationWithUser.class)
                            .collectList()
                            .flatMap(notifications -> {
                                if (!notifications.isEmpty()) {
                                    HttpHeaders headers = new HttpHeaders();
                                    HttpStatus statusCode = HttpStatus.OK;

                                    //add link headers for pagination!! so users can navigate through multiple pages of notifications
                                    String nextLink = createNextLink(notifications, limit, max_id);
                                    String prevLink = createPrevLink(notifications, limit, since_id);

                                    if (nextLink != null) {
                                        headers.add(HttpHeaders.LINK, nextLink);
                                    }

                                    if (prevLink != null) {
                                        headers.add(HttpHeaders.LINK, prevLink);
                                    }

                                    return Mono.just(ResponseEntity.ok().headers(headers).body(notifications));
                                } else {
                                    //return an empty array if there are no notifications
                                    return Mono.just(ResponseEntity.ok().body(Collections.emptyList()));
                                }
                            });
                })
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    private String createNextLink(List<NotificationWithUser> notifications, int limit, String max_id) {
        if (notifications.size() == limit) {
            String nextMaxId = notifications.get(notifications.size() - 1).getId();
            return createLink(limit, "next", nextMaxId, null);
        }
        return null;
    }

    private String createPrevLink(List<NotificationWithUser> notifications, int limit, String since_id) {
        if (notifications.size() == limit && since_id != null) {
            String prevSinceId = notifications.get(0).getId();
            return createLink(limit, "prev", null, prevSinceId);
        }
        return null;
    }

    private String createLink(int limit, String rel, String max_id, String since_id) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath("/api/v1/notifications")
                .queryParam("limit", limit);

        if (max_id != null) {
            uriBuilder.queryParam("max_id", max_id);
        }

        if (since_id != null) {
            uriBuilder.queryParam("since_id", since_id);
        }

        String link = "<" + uriBuilder.build().toUriString() + ">; rel=\"" + rel + "\"";
        return link;
    }
}
