package edu.sjsu.moth.controllers;

import edu.sjsu.moth.server.Main;
import edu.sjsu.moth.server.controller.InstanceController;
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.util.MothConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@WebFluxTest(InstanceController.class)
@ContextConfiguration(classes = { Main.class }, initializers = { InstanceControllerTest.MothTestInitializer.class })
public class InstanceControllerTest {

    @MockBean
    AccountRepository accountRepository;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    public void testGetRules() {
        webTestClient.get()
                .uri("/rules")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(InstanceController.Rule.class)
                .consumeWith(response -> {
                    List<InstanceController.Rule> rules = response.getResponseBody();
                    assertNotNull(rules);
                    //add more assertions if needed
                });
    }

    public static class MothTestInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            try {
                var fullName = InstanceControllerTest.class.getResource("/test.cfg").getFile();
                System.out.println(fullName);
                MothConfiguration mothConfiguration = new MothConfiguration(new File(fullName));
                applicationContext.getBeanFactory().registerSingleton("mothConfiguration", mothConfiguration);
            } catch (Exception e) {
                System.err.println(e.getMessage());
                System.exit(2);
            }
        }
    }
}