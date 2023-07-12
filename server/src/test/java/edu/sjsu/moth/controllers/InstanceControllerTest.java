package edu.sjsu.moth.controllers;

import edu.sjsu.moth.server.controller.InstanceController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@WebFluxTest(InstanceController.class)
public class InstanceControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    public void testGetRules() {
        webTestClient.get().uri("/rules")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(InstanceController.Rule.class)
                .consumeWith(response -> {
                    List<InstanceController.Rule> rules = response.getResponseBody();
                    assertNotNull(rules);
                    //add more assertions if needed
                });
    }
}
