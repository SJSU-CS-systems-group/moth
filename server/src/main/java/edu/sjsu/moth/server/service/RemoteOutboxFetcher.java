package edu.sjsu.moth.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.UnaryOperator;

/**
 * Fetches activities from a remote actor's outbox.
 */
@Service
public class RemoteOutboxFetcher {

    private final WebClient webClient;

    public RemoteOutboxFetcher(WebClient.Builder builder) {
        this.webClient = builder.defaultHeader(HttpHeaders.ACCEPT, "application/activity+json").build();
    }

    public Flux<JsonNode> fetchCreateActivities(String outboxUrl, Integer limit) {
        // apply limit to the flux, or do nothing if the limit is null
        UnaryOperator<Flux<JsonNode>> takeOperator = f -> limit == null ? f : f.take(limit);

        return fetch(outboxUrl).flatMapMany(root -> {
            String type = text(root.path("type"));
            if ("OrderedCollection".equals(type)) {
                String first = text(root.path("first"));
                if (first == null || first.isBlank()) {
                    return Flux.empty();
                }
                return fetchAllPages(first);
            } else {
                return itemsFromPage(root);
            }
        }).filter(this::isCreateNote).transform(takeOperator);
    }

    private Mono<JsonNode> fetch(String url) {
        return webClient.get().uri(url).retrieve().bodyToMono(JsonNode.class);
    }

    private Flux<JsonNode> fetchAllPages(String url) {
        return fetch(url).flatMapMany(page -> {
            Flux<JsonNode> items = itemsFromPage(page);
            String next = text(page.path("next"));
            if (next != null && !next.isBlank()) {
                return items.concatWith(fetchAllPages(next));
            } else {
                return items;
            }
        });
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
