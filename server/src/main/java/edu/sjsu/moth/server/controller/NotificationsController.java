package edu.sjsu.moth.server.controller;

import edu.sjsu.moth.generated.Notification;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
public class NotificationsController {

    private final ReactiveMongoTemplate reactiveMongoTemplate;

    public NotificationsController(ReactiveMongoTemplate reactiveMongoTemplate) {
        this.reactiveMongoTemplate = reactiveMongoTemplate;
    }

    @Document(collection = "notificationwithuser")
    public static class NotificationWithUser {
        Notification notification;
        String user;

        public NotificationWithUser(Notification notification, String user) {
            this.notification = notification;
            this.user = user;
        }

        public String getId() {
            return notification.getId();
        }
    }

    @GetMapping("/api/v1/notifications")
    public Mono<ResponseEntity<List<NotificationWithUser>>> getAllNotifications(Principal principal,
                                                                                @RequestParam(required = false)
                                                                                String max_id,
                                                                                @RequestParam(required = false)
                                                                                String since_id,
                                                                                @RequestParam(required = false)
                                                                                String min_id,
                                                                                @RequestParam(defaultValue = "15")
                                                                                int limit,
                                                                                @RequestParam(required = false)
                                                                                String[] types,
                                                                                @RequestParam(required = false)
                                                                                String[] exclude_types,
                                                                                @RequestParam(required = false)
                                                                                String account_id) {
        if (principal == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }

        int effectiveLimit = Math.min(limit, 30);
        Pageable pageable = PageRequest.of(0, effectiveLimit);

        Query query = new Query();

        query.addCriteria(Criteria.where("user").is(principal.getName()));

        if (account_id != null) {
            query.addCriteria(Criteria.where("account.id").is(account_id));
        }

        if (max_id != null) {
            query.addCriteria(Criteria.where("id").lt(max_id));
        }

        if (since_id != null) {
            query.addCriteria(Criteria.where("id").gt(since_id));
        }

        if (min_id != null) {
            query.addCriteria(Criteria.where("id").gt(min_id));
        }

        query.with(pageable);

        return reactiveMongoTemplate.find(query, Notification.class)
                .map(notification -> new NotificationWithUser(notification, principal.getName())).collectList()
                .flatMap(notifications -> {
                    HttpHeaders headers = new HttpHeaders();

                    String nextLink = createNextLink(notifications, limit, max_id);
                    String prevLink = createPrevLink(notifications, limit, since_id);

                    if (nextLink != null) {
                        headers.add(HttpHeaders.LINK, nextLink);
                    }

                    if (prevLink != null) {
                        headers.add(HttpHeaders.LINK, prevLink);
                    }

                    return Mono.just(ResponseEntity.ok().headers(headers).body(notifications));
                }).defaultIfEmpty(ResponseEntity.ok().body(Collections.emptyList()));
    }

    @GetMapping("/api/v1/notifications/{id}")
    public Mono<ResponseEntity<Notification>> getNotification(Principal principal, @PathVariable String id) {
        if (principal == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }

        Query query = new Query();
        query.addCriteria(Criteria.where("id").is(id));
        query.addCriteria(Criteria.where("user").is(principal.getName()));

        return reactiveMongoTemplate.findOne(query, Notification.class)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/v1/notifications/{id}/dismiss")
    public Mono<ResponseEntity<Object>> dismissNotification(Principal principal, @PathVariable String id) {
        if (principal == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }

        Query query = new Query();
        query.addCriteria(Criteria.where("id").is(id));
        query.addCriteria(Criteria.where("user").is(principal.getName()));

        return reactiveMongoTemplate.remove(query, Notification.class)
                .map(result -> ResponseEntity.ok().build());
    }

    @PostMapping("/api/v1/notifications/clear")
    public Mono<ResponseEntity<Object>> clearNotifications(Principal principal) {
        if (principal == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }

        Query query = new Query();
        query.addCriteria(Criteria.where("user").is(principal.getName()));

        return reactiveMongoTemplate.remove(query, Notification.class)
                .map(result -> ResponseEntity.ok().build());
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
        UriComponentsBuilder uriBuilder =
                UriComponentsBuilder.fromPath("/api/v1/notifications").queryParam("limit", limit);

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
