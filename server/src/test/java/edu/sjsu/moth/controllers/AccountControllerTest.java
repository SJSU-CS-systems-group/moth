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
import edu.sjsu.moth.server.db.Token;
import edu.sjsu.moth.server.db.TokenRepository;
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
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Random;

@SpringBootTest(classes = { AccountControllerTest.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureDataMongo
@ComponentScan(basePackageClasses = MothServerMain.class)
@AutoConfigureWebTestClient
public class AccountControllerTest {
    static private final int RAND_MONGO_PORT = 27017 + new Random().nextInt(17, 37);
    static private TransitionWalker.ReachedState<RunningMongodProcess> eMongod;

    final WebTestClient webTestClient;
    final AccountRepository accountRepository;
    final TokenRepository tokenRepository;

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
    public AccountControllerTest(WebTestClient webTestClient, AccountRepository accountRepository,
                                 TokenRepository tokenRepository) {
        this.webTestClient = webTestClient;
        this.accountRepository = accountRepository;
        this.tokenRepository = tokenRepository;
    }

    /**
     * register an account and a real bearer token for it, since update_credentials
     * authenticates through the opaque token introspector.
     */
    private String setupUserWithToken(String testUser) {
        accountRepository.save(new Account(testUser)).block();
        var token = "test-token-" + testUser;
        tokenRepository.save(new Token(token, testUser, testUser + "@example.com", "test-app", null,
                                       LocalDateTime.now())).block();
        return token;
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

    @Test
    public void testFieldsAttributesIndexCapped() {
        String testUser = "fields-cap-user";
        String token = setupUserWithToken(testUser);

        // a huge index used to allocate a 100001-element fields array
        var builder = new MultipartBodyBuilder();
        builder.part("fields_attributes[100000][name]", "a");
        builder.part("fields_attributes[100000][value]", "b");

        webTestClient.patch()
                .uri("/api/v1/accounts/update_credentials").header("Authorization", "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA).body(BodyInserters.fromMultipartData(builder.build()))
                .exchange().expectStatus().isOk();

        var account = accountRepository.findItemByAcct(testUser).block();
        Assertions.assertNotNull(account);
        Assertions.assertTrue(account.fields == null || account.fields.size() <= 16,
                              "fields should be capped, got " + (account.fields == null ? 0 : account.fields.size()));
    }

    @Test
    public void testFieldsAttributesIndexOverflowIgnored() {
        String testUser = "fields-overflow-user";
        String token = setupUserWithToken(testUser);

        // an index larger than Integer.MAX_VALUE used to 500 with NumberFormatException
        var builder = new MultipartBodyBuilder();
        builder.part("fields_attributes[99999999999][name]", "a");

        webTestClient.patch()
                .uri("/api/v1/accounts/update_credentials").header("Authorization", "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA).body(BodyInserters.fromMultipartData(builder.build()))
                .exchange().expectStatus().isOk();
    }

    @Test
    public void testFieldsAttributesNormalUpdate() {
        String testUser = "fields-normal-user";
        String token = setupUserWithToken(testUser);

        var builder = new MultipartBodyBuilder();
        builder.part("fields_attributes[0][name]", "website");
        builder.part("fields_attributes[0][value]", "https://example.com");
        builder.part("fields_attributes[1][name]", "pronouns");
        builder.part("fields_attributes[1][value]", "they/them");

        webTestClient.patch()
                .uri("/api/v1/accounts/update_credentials").header("Authorization", "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA).body(BodyInserters.fromMultipartData(builder.build()))
                .exchange().expectStatus().isOk();

        var account = accountRepository.findItemByAcct(testUser).block();
        Assertions.assertNotNull(account);
        Assertions.assertEquals(2, account.fields.size());
        Assertions.assertEquals("website", account.fields.get(0).name);
        Assertions.assertEquals("https://example.com", account.fields.get(0).value);
    }
}
