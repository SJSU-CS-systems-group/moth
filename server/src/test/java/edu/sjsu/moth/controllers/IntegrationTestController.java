package edu.sjsu.moth.controllers;

import edu.sjsu.moth.IntegrationTest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
public class IntegrationTestController {
    @GetMapping(IntegrationTest.TOKEN_TEST_ENDPOINT)
    public String index(Principal user) {return "hello sub %s".formatted(user == null ? null : user.getName());}
}
