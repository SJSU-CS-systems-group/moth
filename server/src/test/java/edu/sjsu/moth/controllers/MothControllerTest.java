package edu.sjsu.moth.controllers;

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
import edu.sjsu.moth.server.db.StatusRepository;
import edu.sjsu.moth.server.db.WebfingerAlias;
import edu.sjsu.moth.server.db.WebfingerRepository;
import edu.sjsu.moth.server.util.MothConfiguration;
import edu.sjsu.moth.util.EmailCodeUtils;
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

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@SpringBootTest(classes = { MothControllerTest.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureDataMongo
@ComponentScan(basePackageClasses = MothServerMain.class)
@AutoConfigureWebTestClient
public class MothControllerTest {
    static private final int RAND_MONGO_PORT = 27017 + new Random().nextInt(17, 37);
    static private TransitionWalker.ReachedState<RunningMongodProcess> eMongod;

    final WebTestClient webTestClient;
    final AccountRepository accountRepository;
    final StatusRepository statusRepository;
    final WebfingerRepository webfingerRepository;

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
    public MothControllerTest(WebTestClient webTestClient, AccountRepository accountRepository,
                              StatusRepository statusRepository, WebfingerRepository webfingerRepository) {
        this.webTestClient = webTestClient;
        this.accountRepository = accountRepository;
        this.statusRepository = statusRepository;
        this.webfingerRepository = webfingerRepository;
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

    private Status rawStatus(Account account, String content, String visibility) {
        return new Status(Long.toString(System.nanoTime()), EmailCodeUtils.now(), null, null, false, "", visibility,
                          "en", null, null, 0, 0, 0, false, false, false, false, content, null, null, account,
                          new ArrayList<>(), new ArrayList<>(), List.of(), List.of(), null, null, content,
                          EmailCodeUtils.now());
    }

    @Test
    public void testRssFeedEscapesContentAndSurvivesNulls() {
        String testUser = "rss-user";
        Account account = accountRepository.save(new Account(testUser)).block();
        Assertions.assertNotNull(account);

        // a post that tries to break out of the CDATA section
        var post = new StatusController.V1PostStatus();
        post.status = "before ]]><script>evil</script> after";
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", testUser))).post().uri("/api/v1/statuses")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(post).exchange().expectStatus().isOk();

        // a public status with null content (e.g. ingested remote data) used to NPE the whole feed
        statusRepository.save(rawStatus(account, null, "public")).block();

        var body = webTestClient.get().uri("/@" + testUser + ".rss").exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        Assertions.assertNotNull(body);
        Assertions.assertFalse(body.contains("]]><script>"), "status content must not escape its XML element");

        // the feed must be well-formed XML
        Assertions.assertDoesNotThrow(() -> DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void testWebfingerMalformedResourceIsBadRequest() {
        webTestClient.get().uri("/.well-known/webfinger?resource=garbage-not-acct").exchange().expectStatus()
                .isBadRequest();
    }

    @Test
    public void testWebfingerUnknownUserIsNotFound() {
        webTestClient.get().uri("/.well-known/webfinger?resource=acct:nobody-here@localhost").exchange().expectStatus()
                .isNotFound();
    }

    @Test
    public void testWebfingerKnownUser() {
        webfingerRepository.save(new WebfingerAlias("finger-user", "finger-user", "localhost")).block();

        webTestClient.get().uri("/.well-known/webfinger?resource=acct:finger-user@localhost").exchange().expectStatus()
                .isOk().expectBody().jsonPath("$.links.length()").isEqualTo(2);
    }
}
