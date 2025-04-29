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
import edu.sjsu.moth.server.controller.StatusController;
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

@SpringBootTest(classes = { TimelineControllerTest.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureDataMongo
@ComponentScan(basePackageClasses = MothServerMain.class)
@AutoConfigureWebTestClient
public class TimelineControllerTest {
    public static final String TIMELINE_END_POINTS = "/api/v1/timelines/public";
    public static final String POST_STATUS_ENDPOINT = "/api/v1/statuses";
    static private final int RAND_MONGO_PORT = 27017 + new Random().nextInt(17, 37);
    // https://github.com/flapdoodle-oss/de.flapdoodle.embed.mongo/blob/main/docs/Howto.md documents how to startup
    // embedded mongodb
    static private TransitionWalker.ReachedState<RunningMongodProcess> eMongod;

    final WebTestClient webTestClient;
    final AccountRepository accountRepository;

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
    public TimelineControllerTest(WebTestClient webTestClient, AccountRepository accountRepository) {
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
    public void checkAutoWires() {
        Assertions.assertNotNull(webTestClient);
        Assertions.assertNotNull(accountRepository);
    }

    private void prepareStatus() {
        String statusCreator = "test-user";
        accountRepository.save(new Account(statusCreator)).block();

        StatusController.V1PostStatus request;
        String[] visibilities = { "public", "unlisted", "follower", "direct" };
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
    public void testTimelineGetAllPublicStatus() {
        prepareStatus();
        accountRepository.save(new Account("test-fetch")).block();
        // Mock the authentication
        webTestClient
                .mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "test-fetch")))
                .get()
                .uri(TIMELINE_END_POINTS)
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].visibility").isEqualTo("public");
    }
}
