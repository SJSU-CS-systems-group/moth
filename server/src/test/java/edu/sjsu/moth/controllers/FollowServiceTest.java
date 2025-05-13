package edu.sjsu.moth.controllers;

import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.embed.process.io.ProcessOutput;
import de.flapdoodle.reverse.TransitionWalker;
import de.flapdoodle.reverse.transitions.Start;
import edu.sjsu.moth.IntegrationTest;
import edu.sjsu.moth.generated.Relationship;
import edu.sjsu.moth.server.MothServerMain;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.db.FollowRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.File;
import java.util.Random;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@SpringBootTest(classes = { FollowServiceTest.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureDataMongo
@ComponentScan(basePackageClasses = MothServerMain.class)
@AutoConfigureWebTestClient
public class FollowServiceTest {
    public static final String OTHER_USER = "testuser";
    public static final String CURRENT_USER_ID = "admin";
    public static final String DOES_NOT_EXIST = "does-not-exist";
    public static final String POST_FOLLOW_ENDPOINT = "/api/v1/accounts/{id}/follow";
    public static final String POST_UNFOLLOW_ENDPOINT = "/api/v1/accounts/{id}/unfollow";
    static private final int RAND_MONGO_PORT = 27017 + new Random().nextInt(17, 37);
    // https://github.com/flapdoodle-oss/de.flapdoodle.embed.mongo/blob/main/docs/Howto.md documents how to startup
    // embedded mongodb
    static private TransitionWalker.ReachedState<RunningMongodProcess> eMongod;

    final WebTestClient webTestClient;
    final AccountRepository accountRepository;
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
    public FollowServiceTest(WebTestClient webTestClient, AccountRepository accountRepository,
                             FollowRepository followRepository) {
        this.webTestClient = webTestClient;
        this.accountRepository = accountRepository;
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
        assertNotNull(webTestClient);
        assertNotNull(accountRepository);
        assertNotNull(followRepository);
    }

    @Test
    public void testLocalSendFollowUnfollowHappyPath() {

        accountRepository.save(new Account(OTHER_USER)).block();
        assertThat(followRepository.countAllByFollowedId(OTHER_USER).block()).isEqualTo(0);
        assertThat(followRepository.countAllByFollowerId(CURRENT_USER_ID).block()).isEqualTo(0);
        // Mock the authentication
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", CURRENT_USER_ID))).post()
                .uri(POST_FOLLOW_ENDPOINT, OTHER_USER).contentType(MediaType.APPLICATION_JSON).exchange().expectStatus()
                .isOk().expectBody(Relationship.class).consumeWith(response -> {
                    Relationship relationship = response.getResponseBody();
                    assertNotNull(relationship);
                    assertThat(relationship.following).isTrue();
                    assertThat(relationship.followedBy).isFalse();
                    assertThat(followRepository.findIfFollows(CURRENT_USER_ID, OTHER_USER).block()).isNotNull();
                    assertThat(followRepository.countAllByFollowedId(OTHER_USER).block()).isEqualTo(1);
                    assertThat(followRepository.countAllByFollowerId(CURRENT_USER_ID).block()).isEqualTo(1);
                    assertThat(followRepository.countAllByFollowedId(CURRENT_USER_ID).block()).isEqualTo(0);
                    assertThat(followRepository.countAllByFollowerId(OTHER_USER).block()).isEqualTo(0);
                });

        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", CURRENT_USER_ID))).post()
                .uri(POST_UNFOLLOW_ENDPOINT, OTHER_USER).contentType(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().isOk().expectBody(Relationship.class).consumeWith(response -> {
                    Relationship relationship = response.getResponseBody();
                    assertNotNull(relationship);
                    assertThat(relationship.following).isFalse();
                    assertThat(relationship.followedBy).isFalse();
                    assertThat(followRepository.findIfFollows(CURRENT_USER_ID, OTHER_USER).block()).isNull();
                    assertThat(followRepository.countAllByFollowedId(OTHER_USER).block()).isEqualTo(0);
                    assertThat(followRepository.countAllByFollowerId(CURRENT_USER_ID).block()).isEqualTo(0);
                    assertThat(followRepository.countAllByFollowedId(CURRENT_USER_ID).block()).isEqualTo(0);
                    assertThat(followRepository.countAllByFollowerId(OTHER_USER).block()).isEqualTo(0);
                });
    }

    @Test
    public void testLocalSendFollowUnfollowInvalidUser() throws Exception {
        // 1) Perform the request and capture the raw body
        assertThat(followRepository.countAllByFollowedId(DOES_NOT_EXIST).block()).isEqualTo(0);
        assertThat(followRepository.countAllByFollowerId(CURRENT_USER_ID).block()).isEqualTo(0);
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", CURRENT_USER_ID))).post()
                // make sure you’re supplying a non‐existent account in the path
                .uri(POST_FOLLOW_ENDPOINT, DOES_NOT_EXIST).contentType(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().isNotFound()           // still assert the 404
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND).expectBody().jsonPath("$.message")
                .isEqualTo("Follower or Followed account not found").jsonPath("$.error").isEqualTo("404 NOT_FOUND");

        assertThat(followRepository.findIfFollows(CURRENT_USER_ID, DOES_NOT_EXIST).block()).isNull();
        assertThat(followRepository.findIfFollows(CURRENT_USER_ID, DOES_NOT_EXIST).block()).isNull();
        assertThat(followRepository.countAllByFollowedId(OTHER_USER).block()).isEqualTo(0);
        assertThat(followRepository.countAllByFollowerId(CURRENT_USER_ID).block()).isEqualTo(0);
        assertThat(followRepository.countAllByFollowedId(CURRENT_USER_ID).block()).isEqualTo(0);
        assertThat(followRepository.countAllByFollowerId(DOES_NOT_EXIST).block()).isEqualTo(0);

        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", CURRENT_USER_ID))).post()
                // make sure you’re supplying a non‐existent account in the path
                .uri(POST_UNFOLLOW_ENDPOINT, DOES_NOT_EXIST).contentType(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().isNotFound()           // still assert the 404
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND).expectBody().jsonPath("$.message")
                .isEqualTo("Follower or Followed account not found").jsonPath("$.error").isEqualTo("404 NOT_FOUND");

        assertThat(followRepository.findIfFollows(CURRENT_USER_ID, DOES_NOT_EXIST).block()).isNull();
        assertThat(followRepository.findIfFollows(CURRENT_USER_ID, DOES_NOT_EXIST).block()).isNull();
        assertThat(followRepository.countAllByFollowedId(OTHER_USER).block()).isEqualTo(0);
        assertThat(followRepository.countAllByFollowerId(CURRENT_USER_ID).block()).isEqualTo(0);
        assertThat(followRepository.countAllByFollowedId(CURRENT_USER_ID).block()).isEqualTo(0);
        assertThat(followRepository.countAllByFollowerId(DOES_NOT_EXIST).block()).isEqualTo(0);
    }

    @Test
    public void testLocalSendUnfollowNotFollowingUser() throws Exception {
        // 1) Perform the request and capture the raw body
        accountRepository.save(new Account(OTHER_USER)).block();
        assertThat(followRepository.countAllByFollowedId(OTHER_USER).block()).isEqualTo(0);
        assertThat(followRepository.countAllByFollowerId(CURRENT_USER_ID).block()).isEqualTo(0);
        assertThat(followRepository.findIfFollows(CURRENT_USER_ID, OTHER_USER).block()).isNull();
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", CURRENT_USER_ID))).post()
                .uri(POST_UNFOLLOW_ENDPOINT, OTHER_USER).contentType(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().isNotFound()           // still assert the 404
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND).expectBody().jsonPath("$.message")
                .isEqualTo("No follow relation exists").jsonPath("$.error").isEqualTo("404 NOT_FOUND");
    }

}
