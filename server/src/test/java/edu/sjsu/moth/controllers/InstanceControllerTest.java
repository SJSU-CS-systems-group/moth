package edu.sjsu.moth.controllers;

import edu.sjsu.moth.server.controller.InstanceController;
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.db.TokenRepository;
import edu.sjsu.moth.server.service.AccountService;
import edu.sjsu.moth.server.service.HttpSignatureService;
import edu.sjsu.moth.server.util.ContentSecurityPolicyConfiguration;
import edu.sjsu.moth.util.MothTestInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@WebFluxTest()
@ContextConfiguration(classes = { InstanceController.class,
        ContentSecurityPolicyConfiguration.class }, initializers = MothTestInitializer.class)
public class InstanceControllerTest {

    @MockBean
    TokenRepository tokenRepository;

    @MockBean
    AccountService accountService;

    @MockBean
    AccountRepository accountRepository;

    @MockBean
    HttpSignatureService httpSignatureService;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    public void testGetRules() {
        webTestClient.get().uri("/rules").exchange().expectStatus().isOk().expectBodyList(InstanceController.Rule.class)
                .consumeWith(response -> {
                    List<InstanceController.Rule> rules = response.getResponseBody();
                    assertNotNull(rules);
                    //add more assertions if needed
                });
    }

    @Test
    public void testGetInstanceActivity() {
        webTestClient.get().uri("/api/v1/instance/activity").exchange().expectStatus().isOk()
                .expectBodyList(InstanceController.Activity.class).consumeWith(response -> {
                    List<InstanceController.Activity> activities = response.getResponseBody();
                    assertNotNull(activities);
                });
    }

    @Test
    public void testGetInstancePeers() {
        webTestClient.get().uri("/api/v1/instance/peers").exchange().expectStatus().isOk()
                .expectBodyList(String.class).consumeWith(response -> {
                    List<String> peers = response.getResponseBody();
                    assertNotNull(peers);
                });
    }
}
