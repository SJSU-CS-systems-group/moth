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
import edu.sjsu.moth.server.service.BackfillService;
import edu.sjsu.moth.server.util.MothConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.File;
import java.time.Duration;
import java.util.Random;

import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@SpringBootTest(classes = {
        SearchControllerBackfillTriggerTest.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureDataMongo
@ComponentScan(basePackageClasses = MothServerMain.class)
@AutoConfigureWebTestClient
public class SearchControllerBackfillTriggerTest {

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

    @MockBean
    BackfillService backfillService;

    @Autowired
    public SearchControllerBackfillTriggerTest(WebTestClient webTestClient, AccountRepository accountRepository) {
        this.webTestClient = webTestClient.mutate().responseTimeout(Duration.ofSeconds(30)).build();
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

    @BeforeEach
    public void setupTest() {
        accountRepository.deleteAll().block();
        accountRepository.save(new Account("test-user")).block();
        Mockito.reset(backfillService);
    }

    @Test
    public void resolvesRemoteAndTriggersBackfill_onResolveTrueWithHandle() {
        String query = "@Gargron@mastodon.social";

        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "test-user"))).get()
                .uri(uriBuilder -> uriBuilder.path(SEARCH_ENDPOINT).queryParam("q", query).queryParam("resolve", "true")
                        .build()).exchange().expectStatus().isOk().expectHeader()
                .contentType(MediaType.APPLICATION_JSON);

        Mockito.verify(backfillService, Mockito.timeout(3000))
                .backfillRemoteAcctAsync(eq("Gargron@mastodon.social"), eq(BackfillService.BackfillType.SEARCH));
    }

    @Test
    public void doesNotTriggerBackfill_whenResolveFalse() {
        String query = "@Gargron@mastodon.social";

        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.claim("sub", "test-user"))).get()
                .uri(uriBuilder -> uriBuilder.path(SEARCH_ENDPOINT).queryParam("q", query)
                        .queryParam("resolve", "false").build()).exchange().expectStatus().isOk();

        Mockito.verifyNoInteractions(backfillService);
    }
}
