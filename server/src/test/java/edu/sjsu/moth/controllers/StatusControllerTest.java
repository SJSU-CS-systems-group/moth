package edu.sjsu.moth.controllers;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.embed.process.io.ProcessOutput;
import de.flapdoodle.reverse.TransitionWalker;
import de.flapdoodle.reverse.transitions.Start;
import edu.sjsu.moth.IntegrationTest;
import edu.sjsu.moth.generated.Status;
import edu.sjsu.moth.server.MothServerMain;
import edu.sjsu.moth.server.activitypub.message.CreateMessage;
import edu.sjsu.moth.server.activitypub.message.NoteMessage;
import edu.sjsu.moth.server.controller.StatusController;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.db.Follow;
import edu.sjsu.moth.server.db.FollowRepository;
import edu.sjsu.moth.server.db.OutboxRepository;
import edu.sjsu.moth.server.db.StatusRepository;
import edu.sjsu.moth.server.db.TokenRepository;
import edu.sjsu.moth.server.service.StatusService;
import edu.sjsu.moth.server.util.MothConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import reactor.core.publisher.Mono;

import java.io.File;

import java.util.List;
import java.util.Random;

@SpringBootTest(classes = { StatusControllerTest.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureDataMongo
@ComponentScan(basePackageClasses = MothServerMain.class)
@AutoConfigureWebTestClient
public class StatusControllerTest {
    public static final String POST_STATUS_ENDPOINT = "/api/v1/statuses";
    static private final int RAND_MONGO_PORT = 27017 + new Random().nextInt(17, 37);
    // https://github.com/flapdoodle-oss/de.flapdoodle.embed.mongo/blob/main/docs/Howto.md documents how to startup
    // embedded mongodb
    static private TransitionWalker.ReachedState<RunningMongodProcess> eMongod;

    final WebTestClient webTestClient;
    final TokenRepository tokenRepository;
    final AccountRepository accountRepository;
    final StatusService statusService;
    final StatusRepository statusRepository;
    final FollowRepository followRepository;
    final OutboxRepository outboxRepository;

    static {
        try {
            var fullname = IntegrationTest.class.getResource("/test.cfg").getFile();
            // we need to fake out MothConfiguration
            System.out.println(new MothConfiguration(new File(fullname)).properties);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(2);
        }

    }

    @Autowired
    public StatusControllerTest(WebTestClient webTestClient, TokenRepository tokenRepository,
                                AccountRepository accountRepository, StatusService statusService,
                                StatusRepository statusRepository, FollowRepository followRepository,
                                OutboxRepository outboxRepository) {
        this.webTestClient = webTestClient;
        this.tokenRepository = tokenRepository;
        this.accountRepository = accountRepository;
        this.statusService = statusService;
        this.statusRepository = statusRepository;
        this.followRepository = followRepository;
        this.outboxRepository = outboxRepository;
    }

    @AfterAll
    static void clean() {
        eMongod.close();
    }

    @BeforeAll
    static void setup() {
        eMongod = Mongod.builder().processOutput(Start.to(ProcessOutput.class).initializedWith(ProcessOutput.silent()))
                .net(Start.to(Net.class).initializedWith(Net.defaults().withPort(RAND_MONGO_PORT))).build()
                .start(Version.Main.V6_0);
        System.setProperty("spring.data.mongodb.port", Integer.toString(RAND_MONGO_PORT));
    }

    @Test
    public void checkAutoWires() {
        assertNotNull(webTestClient);
        assertNotNull(tokenRepository);
        assertNotNull(accountRepository);
        assertNotNull(statusService);
        assertNotNull(outboxRepository);
    }

    @Test
    public void testPostStatusError() {
        webTestClient.post().uri(POST_STATUS_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just("{\"status\":\"Hello, world!\"}"), String.class).exchange().expectStatus()
                .isUnauthorized();
    }

    @Test
    public void testPostStatusOk() {
        StatusController.V1PostStatus request = new StatusController.V1PostStatus();
        request.status = "Hello, world!";

        accountRepository.save(new Account("testUser")).block();
        // Mock the authentication
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "testUser"))).post().uri(POST_STATUS_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(request).exchange().expectStatus().isOk();
    }

    @Test
    public void testPostStatusWithMentions() {
        StatusController.V1PostStatus request = new StatusController.V1PostStatus();
        request.status = "Hello, @test-mention @test-mention-2 world!";

        accountRepository.save(new Account("test-user")).block();
        accountRepository.save(new Account("test-mention")).block();
        accountRepository.save(new Account("test-mention-2")).block();
        // Mock the authentication
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "test-user"))).post().uri(POST_STATUS_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(request).exchange().expectStatus().isOk();

        Status status = statusRepository.findByStatusLike("Hello").blockFirst();
        assertNotNull(status);
        assertEquals("test-mention", status.mentions.get(0).acct);
        assertEquals("test-mention-2", status.mentions.get(1).acct);
    }

    @Test
    public void testPostStatusWithRemoteMentions() {
        StatusController.V1PostStatus request = new StatusController.V1PostStatus();
        request.status = "Hello-remote, @test-mention-2@mas.to @test-mention  world!";

        accountRepository.save(new Account("test-user")).block();
        accountRepository.save(new Account("test-mention")).block();
        // Mock the authentication
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "test-user"))).post().uri(POST_STATUS_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(request).exchange().expectStatus().isOk();

        Status status = statusRepository.findByStatusLike("Hello-remote").blockFirst();
        assertNotNull(status);
        assertEquals(1, status.mentions.size());
        assertEquals("test-mention", status.mentions.get(0).acct);
    }

    @Test
    public void testHomeFeedVisibility() {
        String HOME_FEED_END_POINT = "/api/v1/timelines/home";
        prepareStatusForHomeFeedVisibility();
        accountRepository.save(new Account("test-fetch")).block();
        followRepository.save(new Follow("test-fetch", "test-creator")).block();
        // Mock the authentication
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "test-fetch"))).get().uri(HOME_FEED_END_POINT)
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.length()").isEqualTo(3);
    }

    private void prepareStatusForHomeFeedVisibility() {
        String statusCreator = "test-creator";
        accountRepository.save(new Account(statusCreator)).block();

        StatusController.V1PostStatus request;
        String[] visibilities = { "public", "unlisted", "private", "direct" };
        for (String visibility : visibilities) {
            request = new StatusController.V1PostStatus();
            request.status = String.format("This is a %s status", visibility);
            request.visibility = visibility;

            webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", statusCreator))).post()
                    .uri(POST_STATUS_ENDPOINT).contentType(MediaType.APPLICATION_JSON).bodyValue(request).exchange()
                    .expectStatus().isOk();
        }
    }

    @Test
    public void testProfileViewStatusVisbility() {
        String PROFILE_VIEW_END_POINT = "/api/v1/accounts/test-creator-profile-view/statuses";
        prepareStatusForProfileViewVisibility();
        accountRepository.save(new Account("test-fetch")).block();
        accountRepository.save(new Account("test-fetch-no-follow")).block();
        followRepository.save(new Follow("test-fetch", "test-creator-profile-view")).block();
        // Mock the authentication

        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "test-fetch"))).get().uri(PROFILE_VIEW_END_POINT)
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.length()").isEqualTo(3);

        // add a direct post
        StatusController.V1PostStatus directStatus = new StatusController.V1PostStatus();
        directStatus.status = "This is a direct status @test-fetch";
        directStatus.visibility = "direct";

        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "test-creator-profile-view"))).post()
                .uri(POST_STATUS_ENDPOINT).contentType(MediaType.APPLICATION_JSON).bodyValue(directStatus).exchange()
                .expectStatus().isOk();

        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "test-creator-profile-view"))).get()
                .uri(PROFILE_VIEW_END_POINT).exchange().expectStatus().isOk().expectBody().jsonPath("$.length()")
                .isEqualTo(4);

        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "test-fetch"))).get().uri(PROFILE_VIEW_END_POINT)
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.length()").isEqualTo(4);

        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "test-fetch-no-follow"))).get()
                .uri(PROFILE_VIEW_END_POINT).exchange().expectStatus().isOk().expectBody().jsonPath("$.length()")
                .isEqualTo(2);

    }

    private void prepareStatusForProfileViewVisibility() {
        String statusCreator = "test-creator-profile-view";
        accountRepository.save(new Account(statusCreator)).block();

        StatusController.V1PostStatus request;
        String[] visibilities = { "public", "unlisted", "private" };
        for (String visibility : visibilities) {
            request = new StatusController.V1PostStatus();
            request.status = String.format("This is a %s status", visibility);
            request.visibility = visibility;

            webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", statusCreator))).post()
                    .uri(POST_STATUS_ENDPOINT).contentType(MediaType.APPLICATION_JSON).bodyValue(request).exchange()
                    .expectStatus().isOk();
        }
    }

    @Test
    public void testOutboxDataSave() {
        String statusCreator = "test-outbox";
        // 1) create the account
        accountRepository.save(new Account(statusCreator)).block();

        // 2) post one status of each visibility
        String[] visibilities = { "public", "unlisted", "private" };
        for (String visibility : visibilities) {
            StatusController.V1PostStatus request = new StatusController.V1PostStatus();
            request.status = "This is a " + visibility + " status";
            request.visibility = visibility;

            webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", statusCreator))).post()
                    .uri(POST_STATUS_ENDPOINT).contentType(MediaType.APPLICATION_JSON).bodyValue(request).exchange()
                    .expectStatus().isOk();
        }

        // 3) build the actor URL
        String actor = "https://" + MothConfiguration.mothConfiguration.getServerName() + "/users/" + statusCreator;

        // 4) fetch all outbox messages for this actor
        List<CreateMessage> outboxList =
                outboxRepository.findAllByActorOrderByPublishedAtDesc(actor).collectList().block();


        // 5) verify that we stored exactly one Create per post
        assertNotNull(outboxList, "Outbox list should not be null");
        assertEquals(visibilities.length, outboxList.size(), "Expected one outbox entry per status posted");

        // 6) spot‐check the contents of each stored activity
        for (CreateMessage out : outboxList) {
            NoteMessage activity = out.object;
            assertEquals("Create", out.getType(), "Activity type must be Create");
            assertEquals(actor, out.getActor(), "Actor URL must match");
            assertEquals("Note", activity.getType(), "Object type must be Note");
            assertTrue(activity.getContent().contains("This is a"), "Note content missing");
        }
    }

}
