package edu.sjsu.moth.controllers;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
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
import edu.sjsu.moth.server.controller.StatusController;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.db.Follow;
import edu.sjsu.moth.server.db.FollowRepository;
import edu.sjsu.moth.server.db.StatusRepository;
import edu.sjsu.moth.server.db.TokenRepository;
import edu.sjsu.moth.server.service.StatusService;
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

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import reactor.core.publisher.Mono;

import java.io.File;

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
                                StatusRepository statusRepository, FollowRepository followRepository) {
        this.webTestClient = webTestClient;
        this.tokenRepository = tokenRepository;
        this.accountRepository = accountRepository;
        this.statusService = statusService;
        this.statusRepository = statusRepository;
        this.followRepository = followRepository;
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
        Assertions.assertNotNull(webTestClient);
        Assertions.assertNotNull(tokenRepository);
        Assertions.assertNotNull(accountRepository);
        Assertions.assertNotNull(statusService);
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
        Assertions.assertNotNull(status);
        Assertions.assertEquals("test-mention", status.mentions.get(0).acct);
        Assertions.assertEquals("test-mention-2", status.mentions.get(1).acct);
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
        Assertions.assertNotNull(status);
        Assertions.assertEquals(1, status.mentions.size());
        Assertions.assertEquals("test-mention", status.mentions.get(0).acct);
    }

    @Test
    public void testHomefeed() {
        String HOMEFEED_END_POINT = "/api/v1/timelines/home";
        prepareStatusHomeFeed();
        accountRepository.save(new Account("test-fetch")).block();
        followRepository.save(new Follow("test-fetch", "test-creator")).block();
        // Mock the authentication
        webTestClient
                .mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "test-fetch")))
                .get()
                .uri(HOMEFEED_END_POINT)
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(3);
    }

    private void prepareStatusHomeFeed() {
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

}
