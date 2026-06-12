package edu.sjsu.moth.controllers;

import com.fasterxml.jackson.databind.JsonNode;
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
import edu.sjsu.moth.server.db.UserListRepository;
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
import java.util.Map;
import java.util.Random;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@SpringBootTest(classes = { ListsControllerTest.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureDataMongo
@ComponentScan(basePackageClasses = MothServerMain.class)
@AutoConfigureWebTestClient
public class ListsControllerTest {
    static private final int RAND_MONGO_PORT = 27017 + new Random().nextInt(17, 37);
    static private TransitionWalker.ReachedState<RunningMongodProcess> eMongod;

    final WebTestClient webTestClient;
    final AccountRepository accountRepository;
    final UserListRepository userListRepository;

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
    public ListsControllerTest(WebTestClient webTestClient, AccountRepository accountRepository,
                               UserListRepository userListRepository) {
        this.webTestClient = webTestClient;
        this.accountRepository = accountRepository;
        this.userListRepository = userListRepository;
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

    private String createList(String owner, String title) {
        var body = webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", owner))).post()
                .uri("/api/v1/lists").contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("title", title))
                .exchange().expectStatus().isOk().expectBody(JsonNode.class).returnResult().getResponseBody();
        Assertions.assertNotNull(body);
        return body.get("id").asText();
    }

    @Test
    public void testListEndpointsHiddenFromNonOwners() {
        accountRepository.save(new Account("list-owner")).block();
        accountRepository.save(new Account("list-intruder")).block();
        var listId = createList("list-owner", "private people");

        // the owner can read their list
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "list-owner"))).get()
                .uri("/api/v1/lists/" + listId).exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.title").isEqualTo("private people");
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "list-owner"))).get()
                .uri("/api/v1/lists/" + listId + "/accounts").exchange().expectStatus().isOk();
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "list-owner"))).get()
                .uri("/api/v1/timelines/list/" + listId).exchange().expectStatus().isOk();

        // another user must not see it
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "list-intruder"))).get()
                .uri("/api/v1/lists/" + listId).exchange().expectStatus().isNotFound();
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "list-intruder"))).get()
                .uri("/api/v1/lists/" + listId + "/accounts").exchange().expectStatus().isNotFound();
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "list-intruder"))).get()
                .uri("/api/v1/timelines/list/" + listId).exchange().expectStatus().isNotFound();
    }

    @Test
    public void testListTimelineShowsMemberStatuses() {
        accountRepository.save(new Account("timeline-owner")).block();
        var member = accountRepository.save(new Account("timeline-member")).block();
        Assertions.assertNotNull(member);
        var listId = createList("timeline-owner", "follows");

        // owner adds the member account to the list
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "timeline-owner"))).post()
                .uri("/api/v1/lists/" + listId + "/accounts").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("account_ids", java.util.List.of(member.id))).exchange().expectStatus().isOk();

        // member posts a status
        var post = new StatusController.V1PostStatus();
        post.status = "hello from the list member";
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "timeline-member"))).post()
                .uri("/api/v1/statuses").contentType(MediaType.APPLICATION_JSON).bodyValue(post).exchange()
                .expectStatus().isOk();

        // owner sees the member's status in the list timeline
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "timeline-owner"))).get()
                .uri("/api/v1/timelines/list/" + listId).exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].content").isEqualTo("hello from the list member");
    }
}
