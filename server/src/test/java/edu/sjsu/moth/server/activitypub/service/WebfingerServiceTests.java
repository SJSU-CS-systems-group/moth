package edu.sjsu.moth.server.activitypub.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class WebfingerServiceTests {

    @Test
    public void testDiscoverProfileUrl() {
        WebfingerService webfingerService = new WebfingerService(WebClient.builder());
        String userHandle = "divyamonmastodon@mastodon.social";
        Mono<String> profileUrlMono = webfingerService.discoverProfileUrl(userHandle);

        StepVerifier.create(profileUrlMono).expectNext("https://mastodon.social/users/divyamonmastodon")
                .verifyComplete();
    }

    @Test
    public void testDiscoverProfileUrlNotFound() {
        WebfingerService webfingerService = new WebfingerService(WebClient.builder());
        String userHandle = "nonexistentuser@mastodon.social";
        Mono<String> profileUrlMono = webfingerService.discoverProfileUrl(userHandle);

        StepVerifier.create(profileUrlMono).verifyComplete();
    }
}