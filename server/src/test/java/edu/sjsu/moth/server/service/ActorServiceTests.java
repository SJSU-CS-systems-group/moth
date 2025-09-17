package edu.sjsu.moth.server.service;

import edu.sjsu.moth.server.activitypub.service.WebfingerService;
import edu.sjsu.moth.server.db.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class ActorServiceTests {

    private ActorService actorService;

    @BeforeEach
    public void setUp() {
        WebClient.Builder webClientBuilder = WebClient.builder();
        WebfingerService webfingerService = new WebfingerService(webClientBuilder);
        actorService = new ActorService(null, webfingerService, webClientBuilder);
    }

    @Test
    public void resolveRemoteAccount_minimal() {
        Mono<Account> mono = actorService.resolveRemoteAccount("gargron@mastodon.social")
                .doOnNext(a -> System.out.println("username=" + a.username + ", acct=" + a.acct + ", url=" + a.url));

        StepVerifier.create(mono).assertNext(a -> {
            // username is preferredUsername
            assert a.username != null && !a.username.isBlank();
            assert a.username.equalsIgnoreCase("gargron");

            // acct is FULL handle
            assert a.acct != null && !a.acct.isBlank();
            assert a.acct.equalsIgnoreCase("gargron@mastodon.social");

            // sanity: URL should be from valid server! -> mastodon.social
            assert a.url != null && a.url.contains("mastodon.social");
        }).verifyComplete();
    }
}
