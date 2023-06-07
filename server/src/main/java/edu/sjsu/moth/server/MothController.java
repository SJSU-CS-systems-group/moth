package edu.sjsu.moth.server;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MothController {

    @GetMapping("/")
    public String index() { return "hello"; }

}
