package edu.sjsu.moth.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Fetches the first page of a remote actor's outbox and returns Create activities.
 */
@Service
public class RemoteOutboxFetcher {

    private final WebClient webClient;

    public RemoteOutboxFetcher(WebClient.Builder builder) {
        this.webClient = builder.defaultHeader(HttpHeaders.ACCEPT, "application/activity+json").build();
    }

    public Flux<JsonNode> fetchCreateActivities(String outboxUrl, int limit) {
        return fetch(outboxUrl).flatMapMany(root -> {
            String type = text(root.path("type"));
            if ("OrderedCollection".equals(type)) {
                String first = text(root.path("first"));
                if (first == null || first.isBlank()) {
                    return Flux.empty();
                }
                return fetch(first).flatMapMany(this::itemsFromPage);
            } else {
                return itemsFromPage(root);
            }
        }).filter(this::isCreateNote).take(limit);
    }

    private Mono<JsonNode> fetch(String url) {
        return webClient.get().uri(url).retrieve().bodyToMono(JsonNode.class);
    }

    private Flux<JsonNode> itemsFromPage(JsonNode page) {
        JsonNode items = page.path("orderedItems");
        if (items == null || !items.isArray()) return Flux.empty();
        return Flux.fromIterable(items);
    }

    private boolean isCreateNote(JsonNode item) {
        return "Create".equals(text(item.path("type"))) && "Note".equals(text(item.path("object").path("type")));
    }

    private String text(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }
}
