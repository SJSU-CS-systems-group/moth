package edu.sjsu.moth.controllers;

import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.embed.process.io.ProcessOutput;
import de.flapdoodle.reverse.TransitionWalker;
import de.flapdoodle.reverse.transitions.Start;
import edu.sjsu.moth.IntegrationTest;
import edu.sjsu.moth.server.MothServerMain;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.util.MothConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.File;
import java.util.Random;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@SpringBootTest(classes = { SearchControllerTest.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureDataMongo
@ComponentScan(basePackageClasses = MothServerMain.class)
@AutoConfigureWebTestClient
public class SearchControllerTest {

    public static final String SEARCH_ENDPOINT = "/api/v2/search";
    static private final int RAND_MONGO_PORT = 27017 + new Random().nextInt(17, 37);
    static private TransitionWalker.ReachedState<RunningMongodProcess> eMongod;

    final WebTestClient webTestClient;
    final AccountRepository accountRepository;

    static {
        try {
            var fullname = IntegrationTest.class.getResource("/test.cfg").getFile();
            System.out.println(new MothConfiguration(new File(fullname)).properties);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(2);
        }
    }

    @Autowired
    public SearchControllerTest(WebTestClient webTestClient, AccountRepository accountRepository) {
        this.webTestClient = webTestClient;
        this.accountRepository = accountRepository;
    }

    @BeforeAll
    static void setup() {
        eMongod = Mongod.builder().processOutput(Start.to(ProcessOutput.class).initializedWith(ProcessOutput.silent()))
                .net(Start.to(Net.class).initializedWith(Net.defaults().withPort(RAND_MONGO_PORT))).build()
                .start(Version.Main.V6_0);
        System.setProperty("spring.data.mongodb.port", Integer.toString(RAND_MONGO_PORT));
    }

    @AfterAll
    static void clean() {
        eMongod.close();
    }

    @Test
    public void checkAutoWires() {
        Assertions.assertNotNull(webTestClient);
        Assertions.assertNotNull(accountRepository);
    }

    @Test
    public void testSearchQueryTooShortReturnsEmpty() {
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "test-user"))).get()
                .uri(uriBuilder -> uriBuilder.path(SEARCH_ENDPOINT)
                        .queryParam("q", "ab").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accounts.length()").isEqualTo(0)
                .jsonPath("$.statuses.length()").isEqualTo(0);
    }

    @Test
    public void testSearchLocalAccount() {
        accountRepository.save(new Account("test-local")).block();

        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "test-local"))).get()
                .uri(uriBuilder -> uriBuilder.path(SEARCH_ENDPOINT)
                        .queryParam("q", "test-local")
                        .queryParam("type", "accounts")
                        .build())

                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accounts[0].acct").isEqualTo("test-local");
    }

    @Test
    public void testRemoteSearchWithInvalidDomainReturnsEmpty() {
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "test-user"))).get()
                .uri(uriBuilder -> uriBuilder.path(SEARCH_ENDPOINT)
                        .queryParam("q", "@user@invalid_domain")
                        .build())

                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(response -> Assertions.assertTrue(response.getResponseBody() == null || response.getResponseBody().length == 0));
    }

    // Optional: For remote test with real Mastodon instance (only for manual testing)
    @Test
    public void testRemoteSearchValidDomain() {
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "test-user"))).get()
                .uri(uriBuilder -> uriBuilder.path(SEARCH_ENDPOINT)
                        .queryParam("q", "@Gargron@mastodon.social")
                        .build())

                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accounts").exists(); // Will vary depending on actual Mastodon availability
    }

    @Test
    public void testOnlyRemoteUsersAreSaved() {
        String localDomain = "moth.vamshiraj.me"; // Replace with actual domain logic used in app

        // Step 1: Clear DB
        accountRepository.deleteAll().block();

        // Step 2: Make a remote search request (simulate via real domain, or mock if isolated)
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "test-user"))).get()
                .uri(uriBuilder -> uriBuilder
                        .path(SEARCH_ENDPOINT)
                        .queryParam("q", "@Gargron@mastodon.social") // Example remote user
                        .queryParam("type", "accounts")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accounts").exists();

        // Step 3: Assert only remote users are saved
        var allAccounts = accountRepository.findAll().collectList().block();
        Assertions.assertNotNull(allAccounts);
        Assertions.assertFalse(allAccounts.isEmpty());

        for (Account acc : allAccounts) {
            System.out.println("Saved account: " + acc.acct);
            Assertions.assertTrue(acc.acct.contains("@"), "Remote user acct should include domain");
            Assertions.assertFalse(acc.url.contains(localDomain), "Should not save local users");
        }
    }

}
