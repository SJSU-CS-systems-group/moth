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
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.util.MothConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.File;
import java.util.Random;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@SpringBootTest(classes = { TagsControllerTest.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureDataMongo
@ComponentScan(basePackageClasses = MothServerMain.class)
@AutoConfigureWebTestClient
public class TagsControllerTest {
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
    public TagsControllerTest(WebTestClient webTestClient, AccountRepository accountRepository) {
        this.webTestClient = webTestClient;
        this.accountRepository = accountRepository;
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
    public void testGetFollowedTags() {
        String testUser = "tags-test-user";
        accountRepository.save(new Account(testUser)).block();

        webTestClient
                .mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", testUser)))
                .get()
                .uri("/api/v1/followed_tags")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(0);
    }

    @Test
    public void testGetFollowedTagsUnauthorized() {
        webTestClient
                .get()
                .uri("/api/v1/followed_tags")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    public void testGetFeaturedTags() {
        String testUser = "featured-tags-test-user";
        accountRepository.save(new Account(testUser)).block();

        webTestClient
                .mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", testUser)))
                .get()
                .uri("/api/v1/featured_tags")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(0);
    }

    @Test
    public void testGetTag() {
        webTestClient
                .get()
                .uri("/api/v1/tags/test")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("test")
                .jsonPath("$.following").isEqualTo(false)
                // url must be built from the configured server name, not hardcoded
                .jsonPath("$.url").isEqualTo(edu.sjsu.moth.server.controller.MothController.BASE_URL + "/tags/test");
    }

    @Test
    public void testFollowTag() {
        String testUser = "follow-tag-test-user";
        accountRepository.save(new Account(testUser)).block();

        webTestClient
                .mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", testUser)))
                .post()
                .uri("/api/v1/tags/testtag/follow")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("testtag")
                .jsonPath("$.following").isEqualTo(true);
    }

    @Test
    public void testUnfollowTag() {
        String testUser = "unfollow-tag-test-user";
        accountRepository.save(new Account(testUser)).block();

        webTestClient
                .mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", testUser)))
                .post()
                .uri("/api/v1/tags/testtag/unfollow")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("testtag")
                .jsonPath("$.following").isEqualTo(false);
    }
}
