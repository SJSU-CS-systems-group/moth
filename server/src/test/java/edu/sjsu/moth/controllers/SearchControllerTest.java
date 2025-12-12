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
import edu.sjsu.moth.server.db.ExternalActorRepository;
import edu.sjsu.moth.server.util.MothConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.File;
import java.time.Duration;
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

    static {
        try {
            var fullname = IntegrationTest.class.getResource("/test.cfg").getFile();
            System.out.println(new MothConfiguration(new File(fullname)).properties);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(2);
        }
    }

    final WebTestClient webTestClient;
    final AccountRepository accountRepository;
    final ExternalActorRepository externalActorRepository;

    @Autowired
    public SearchControllerTest(WebTestClient webTestClient, AccountRepository accountRepository,
                                ExternalActorRepository externalActorRepository) {
        // Increase the timeout to prevent flaky tests when hitting live servers.
        this.webTestClient = webTestClient.mutate().responseTimeout(Duration.ofSeconds(30)).build();
        this.accountRepository = accountRepository;
        this.externalActorRepository = externalActorRepository;
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

    @BeforeEach
    public void setupTest() {
        // clear repositories before each test
        accountRepository.deleteAll().block();
        externalActorRepository.deleteAll().block();
        // for the mock authentication to work
        accountRepository.save(new Account("test-user")).block();
    }

    @Test
    public void testSearchQueryTooShortReturnsEmpty() {
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "test-user"))).get()
                .uri(uriBuilder -> uriBuilder.path(SEARCH_ENDPOINT).queryParam("q", "ab").build()).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.accounts.length()").isEqualTo(0)
                .jsonPath("$.statuses.length()").isEqualTo(0);
    }

    @Test
    public void testSearchLocalAccount() {
        accountRepository.save(new Account("test-local")).block();

        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "test-local"))).get()
                .uri(uriBuilder -> uriBuilder.path(SEARCH_ENDPOINT).queryParam("q", "test-local")
                        .queryParam("type", "accounts").build())

                .exchange().expectStatus().isOk().expectBody().jsonPath("$.accounts[0].acct").isEqualTo("test-local");
    }

    @Test
    public void testRemoteSearchWithInvalidDomainReturnsEmpty() {
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "test-user"))).get()
                .uri(uriBuilder -> uriBuilder.path(SEARCH_ENDPOINT).queryParam("q", "@user@invalid_domain")
                        .queryParam("resolve", "true") // IMPORTANT: resolve must be true
                        .build()).exchange().expectStatus().isOk().expectHeader()
                .contentType(MediaType.APPLICATION_JSON).expectBody().jsonPath("$.accounts").isEmpty()
                .jsonPath("$.statuses").isEmpty().jsonPath("$.hashtags").isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "@Gargron@mastodon.social", // resolve by handle
            "https://mastodon.social/@Gargron" // resolve by user-facing URL
    })
    public void testRemoteSearch(String query) {
        // make a remote search
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "test-user"))).get()
                .uri(uriBuilder -> uriBuilder.path(SEARCH_ENDPOINT).queryParam("q", query)
                        .queryParam("resolve", "true") // IMPORTANT
                        .build()).exchange().expectStatus().isOk().expectBody().jsonPath("$.accounts[0].acct")
                .isEqualTo("Gargron@mastodon.social");

        // Step 2: Assert only the authenticated user is in the local AccountRepository
        var allAccounts = accountRepository.findAll().collectList().block();
        Assertions.assertEquals(1, allAccounts.size(), "Only the test-user should be in the AccountRepository");

        // Step 3: Assert the remote Actor was saved
        String expectedCanonicalActorId = "https://mastodon.social/users/Gargron";
        var savedActor = externalActorRepository.findItemById(expectedCanonicalActorId).block();
        Assertions.assertNotNull(savedActor, "Expected actor to be saved with ID: " + expectedCanonicalActorId);
        Assertions.assertEquals(expectedCanonicalActorId, savedActor.id);
    }

    @Test
    public void testInvalidProfileUrlReturnsEmpty() {
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "test-user"))).get()
                .uri(uriBuilder -> uriBuilder.path(SEARCH_ENDPOINT)
                        .queryParam("q", "https://invalid.example/@doesnotexist")
                        .queryParam("resolve", "true") // IMPORTANT
                        .build()).exchange().expectStatus().isOk().expectBody().jsonPath("$.accounts.length()")
                .isEqualTo(0);
    }
}
