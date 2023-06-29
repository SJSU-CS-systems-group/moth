package edu.sjsu.moth;

import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.reverse.TransitionWalker;
import de.flapdoodle.reverse.transitions.Start;
import edu.sjsu.moth.controllers.IntegrationTestController;
import edu.sjsu.moth.server.Main;
import edu.sjsu.moth.server.db.Token;
import edu.sjsu.moth.server.db.TokenRepository;
import edu.sjsu.moth.server.util.MothConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@AutoConfigureDataMongo
// not sure why i need to pass IntegrationTestController here, i thought it would autodetect...
@SpringBootTest(classes = { Main.class, IntegrationTestController.class }, webEnvironment =
        SpringBootTest.WebEnvironment.RANDOM_PORT)
public class IntegrationTest {
    public static final String TOKEN_TEST_ENDPOINT = "/token/test";
    // https://github.com/flapdoodle-oss/de.flapdoodle.embed.mongo/blob/main/docs/Howto.md documents how to startup
    // embedded mongodb
    static private TransitionWalker.ReachedState<RunningMongodProcess> eMongod;
    @Autowired
    WebTestClient webTestClient;
    @Autowired
    TokenRepository tokenRepository;

    static {
        try {
            var fullname = IntegrationTest.class.getResource("/test.cfg").getFile();
            // we need to fake out MothConfiguration
            System.out.println(new MothConfiguration(fullname).properties);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(2);
        }

    }

    @AfterAll
    static void clean() {
        eMongod.close();
    }

    @BeforeAll
    static void setup() throws Exception {
        String ip = "localhost";
        int port = 27017;
        eMongod = Mongod.builder()
                .net(Start.to(Net.class).initializedWith(Net.defaults().withPort(27017)))
                .build()
                .start(Version.Main.PRODUCTION);
    }

    @Test
    public void checkAutoWires() {
        Assertions.assertNotNull(webTestClient);
        Assertions.assertNotNull(tokenRepository);
    }

    @Test
    public void testHelloBearerToken() {
        // Since there is no bearer token, the user should be null
        var body = webTestClient.get().uri(TOKEN_TEST_ENDPOINT)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();
        Assertions.assertEquals("hello sub null", new String(body));

        // Now try with a token that we put in the database, we should see the user dude
        tokenRepository.save(new Token("XXXX", "dude")).block();
        body = webTestClient.get().uri(TOKEN_TEST_ENDPOINT)
                .header("Authorization", "Bearer XXXX")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();
        Assertions.assertEquals("hello sub dude", new String(body));

        // Now try with a token not in the database
        webTestClient.get()
                .uri(TOKEN_TEST_ENDPOINT)
                .header("Authorization", "Bearer YYYY")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }
}