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
import edu.sjsu.moth.server.db.EmailRegistration;
import edu.sjsu.moth.server.db.EmailRegistrationRepository;
import edu.sjsu.moth.server.util.MothConfiguration;
import edu.sjsu.moth.util.EmailCodeUtils;
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

import java.io.File;
import java.util.Random;

@SpringBootTest(classes = { OAuthControllerTest.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureDataMongo
@ComponentScan(basePackageClasses = MothServerMain.class)
@AutoConfigureWebTestClient
public class OAuthControllerTest {
    static private final int RAND_MONGO_PORT = 27017 + new Random().nextInt(17, 37);
    static private TransitionWalker.ReachedState<RunningMongodProcess> eMongod;

    final WebTestClient webTestClient;
    final AccountRepository accountRepository;
    final EmailRegistrationRepository emailRegistrationRepository;

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
    public OAuthControllerTest(WebTestClient webTestClient, AccountRepository accountRepository,
                                EmailRegistrationRepository emailRegistrationRepository) {
        this.webTestClient = webTestClient;
        this.accountRepository = accountRepository;
        this.emailRegistrationRepository = emailRegistrationRepository;
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
    public void testPasswordGrantType() {
        // Create a test user account
        String testUser = "oauth-test-user";
        String testEmail = "oauthtest@localhost";
        String testPassword = "testpassword123";

        Account account = new Account(testUser);
        accountRepository.save(account).block();

        // Create email registration with password
        EmailRegistration reg = new EmailRegistration();
        reg.id = EmailCodeUtils.normalizeEmail(testEmail);
        reg.email = testEmail;
        reg.username = testUser;
        reg.saltedPassword = EmailCodeUtils.encodePassword(testPassword);
        emailRegistrationRepository.save(reg).block();

        // Step 1: Register an app
        var appResponse = webTestClient.post()
                .uri("/api/v1/apps")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("client_name=TestApp&redirect_uris=urn:ietf:wg:oauth:2.0:oob&scopes=read write follow")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.client_id").isNotEmpty()
                .jsonPath("$.client_secret").isNotEmpty()
                .returnResult()
                .getResponseBody();

        // Parse client_id and client_secret from response
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String clientId;
        String clientSecret;
        try {
            var json = mapper.readTree(appResponse);
            clientId = json.get("client_id").asText();
            clientSecret = json.get("client_secret").asText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Step 2: Get token using password grant type
        var tokenResponse = webTestClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("grant_type=password&username=" + testEmail + "&password=" + testPassword +
                        "&client_id=" + clientId + "&client_secret=" + clientSecret + "&scope=read write follow")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.access_token").isNotEmpty()
                .jsonPath("$.token_type").isEqualTo("Bearer")
                .returnResult()
                .getResponseBody();

        // Parse access_token from response
        String accessToken;
        try {
            var json = mapper.readTree(tokenResponse);
            accessToken = json.get("access_token").asText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Step 3: Verify credentials using the token
        webTestClient.get()
                .uri("/api/v1/accounts/verify_credentials")
                .header("Authorization", "Bearer " + accessToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.username").isEqualTo(testUser)
                .jsonPath("$.acct").isEqualTo(testUser);
    }

    @Test
    public void testPasswordGrantWrongClientSecretRejected() {
        String testUser = "oauth-secret-user";
        String testEmail = "oauthsecret@localhost";
        String testPassword = "testpassword123";

        accountRepository.save(new Account(testUser)).block();
        EmailRegistration reg = new EmailRegistration();
        reg.id = EmailCodeUtils.normalizeEmail(testEmail);
        reg.email = testEmail;
        reg.username = testUser;
        reg.saltedPassword = EmailCodeUtils.encodePassword(testPassword);
        emailRegistrationRepository.save(reg).block();

        var appResponse = webTestClient.post()
                .uri("/api/v1/apps")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("client_name=TestApp&redirect_uris=urn:ietf:wg:oauth:2.0:oob&scopes=read")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();

        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String clientId;
        try {
            clientId = mapper.readTree(appResponse).get("client_id").asText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // valid user credentials but a wrong client_secret for a known client_id must be rejected
        webTestClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("grant_type=password&username=" + testEmail + "&password=" + testPassword +
                        "&client_id=" + clientId + "&client_secret=WRONG-SECRET&scope=read")
                .exchange()
                .expectStatus().is4xxClientError();
    }

    @Test
    public void testPasswordGrantNoClientIdStillWorks() {
        // toot login_cli after a server restart presents a client_id the server no longer knows;
        // the permissive path (no registered client) must keep working
        String testUser = "oauth-noclient-user";
        String testEmail = "oauthnoclient@localhost";
        String testPassword = "testpassword123";

        accountRepository.save(new Account(testUser)).block();
        EmailRegistration reg = new EmailRegistration();
        reg.id = EmailCodeUtils.normalizeEmail(testEmail);
        reg.email = testEmail;
        reg.username = testUser;
        reg.saltedPassword = EmailCodeUtils.encodePassword(testPassword);
        emailRegistrationRepository.save(reg).block();

        webTestClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("grant_type=password&username=" + testEmail + "&password=" + testPassword + "&scope=read")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.access_token").isNotEmpty();
    }

    @Test
    public void testPasswordGrantTypeInvalidCredentials() {
        String testEmail = "invalid@localhost";
        String testPassword = "wrongpassword";

        // Register an app first
        var appResponse = webTestClient.post()
                .uri("/api/v1/apps")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("client_name=TestApp&redirect_uris=urn:ietf:wg:oauth:2.0:oob&scopes=read")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();

        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String clientId;
        String clientSecret;
        try {
            var json = mapper.readTree(appResponse);
            clientId = json.get("client_id").asText();
            clientSecret = json.get("client_secret").asText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Try password grant with invalid credentials - should fail
        webTestClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("grant_type=password&username=" + testEmail + "&password=" + testPassword +
                        "&client_id=" + clientId + "&client_secret=" + clientSecret + "&scope=read")
                .exchange()
                .expectStatus().is4xxClientError();
    }
}
