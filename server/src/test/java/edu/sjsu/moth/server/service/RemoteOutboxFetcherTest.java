package edu.sjsu.moth.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;

public class RemoteOutboxFetcherTest {

    @Test
    public void testFetchCreateActivitiesWithPaginationUsingMockExchangeFunction() {
        final String baseUrl = "https://example.com";
        final String outboxUrl = baseUrl + "/outbox";
        final String page1Url = baseUrl + "/page1";
        final String page2Url = baseUrl + "/page2";

        // JSON payloads for the mock responses
        String outboxJson = """
                {
                  "@context": "https://www.w3.org/ns/activitystreams",
                  "id": "%s",
                  "type": "OrderedCollection",
                  "totalItems": 3,
                  "first": "%s"
                }
                """.formatted(outboxUrl, page1Url);

        String page1Json = """
                {
                  "@context": "https://www.w3.org/ns/activitystreams",
                  "id": "%s",
                  "type": "OrderedCollectionPage",
                  "next": "%s",
                  "partOf": "%s",
                  "orderedItems": [
                    { "id": "https://example.com/activities/1", "type": "Create", "object": { "type": "Note" } },
                    { "id": "https://example.com/activities/2", "type": "Create", "object": { "type": "Note" } }
                  ]
                }
                """.formatted(page1Url, page2Url, outboxUrl);

        String page2Json = """
                {
                  "@context": "https://www.w3.org/ns/activitystreams",
                  "id": "%s",
                  "type": "OrderedCollectionPage",
                  "partOf": "%s",
                  "orderedItems": [
                    { "id": "https://example.com/activities/3", "type": "Create", "object": { "type": "Note" } }
                  ]
                }
                """.formatted(page2Url, outboxUrl);

        // Mock ExchangeFunction to simulate server responses
        ExchangeFunction mockExchangeFunction = request -> {
            String url = request.url().toString();
            String responseBody;
            if (url.equals(outboxUrl)) {
                responseBody = outboxJson;
            } else if (url.equals(page1Url)) {
                responseBody = page1Json;
            } else if (url.equals(page2Url)) {
                responseBody = page2Json;
            } else {
                return Mono.error(new IOException("Unexpected request to " + url));
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                                     .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                     .body(responseBody).build());
        };

        // Create a WebClient.Builder with the mocked ExchangeFunction
        WebClient.Builder testWebClientBuilder = WebClient.builder().exchangeFunction(mockExchangeFunction);
        RemoteOutboxFetcher fetcher = new RemoteOutboxFetcher(testWebClientBuilder);

        // Run the test
        Flux<JsonNode> activities = fetcher.fetchCreateActivities(outboxUrl, 5);

        // Verify the results
        StepVerifier.create(activities)
                .expectNextMatches(node -> node.get("id").asText().equals("https://example.com/activities/1"))
                .expectNextMatches(node -> node.get("id").asText().equals("https://example.com/activities/2"))
                .expectNextMatches(node -> node.get("id").asText().equals("https://example.com/activities/3"))
                .verifyComplete();
    }

    @Test
    public void testFetchGargronOutboxAndPrint() {
        WebClient.Builder realWebClientBuilder = WebClient.builder();
        RemoteOutboxFetcher fetcher = new RemoteOutboxFetcher(realWebClientBuilder);

        String gargronOutboxUrl = "https://mastodon.social/users/Gargron/outbox";

        // Fetch activities from Gargron's outbox with no limit (he posted 80K statuses so far)!!
        Flux<JsonNode> activities = fetcher.fetchCreateActivities(gargronOutboxUrl, null);

        Flux<JsonNode> testFlux = activities.take(50); // Limit to 50 to keep the test reasonably fast.

        // Verify that we received 50 items.
        StepVerifier.create(testFlux).expectNextCount(50).verifyComplete();
    }
}