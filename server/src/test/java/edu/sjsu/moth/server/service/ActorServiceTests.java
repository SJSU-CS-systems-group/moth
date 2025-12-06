package edu.sjsu.moth.server.service;

import edu.sjsu.moth.server.activitypub.service.WebfingerService;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.ExternalActorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class ActorServiceTests {

    private ActorService actorService;

    @BeforeEach
    public void setUp() {
        WebClient.Builder webClientBuilder = WebClient.builder();
        WebfingerService webfingerService = new WebfingerService(webClientBuilder);
        ExternalActorRepository externalActorRepository = Mockito.mock(ExternalActorRepository.class);
        when(externalActorRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        AccountService mockAccountService = Mockito.mock(AccountService.class);
        Account account = new Account();
        account.username = "gargron";
        account.acct = "gargron@mastodon.social";
        account.url = "https://mastodon.social/@gargron";
        when(mockAccountService.convertToAccount(any())).thenReturn(Mono.just(account));

        actorService = new ActorService(externalActorRepository, webfingerService, webClientBuilder);
        ReflectionTestUtils.setField(actorService, "accountService", mockAccountService);
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
