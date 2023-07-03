package edu.sjsu.moth.server.controller;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import java.io.IOException;

@Configuration
@Controller
public class FilesController implements WebFluxConfigurer {
    public static String userFileURL(String user, String file) {
        return MothController.BASE_URL + "/files/users/" + user + "/" + file;
    }

    public static String instanceFileURL(String file) {
        return MothController.BASE_URL + "/files/instance/" + file;
    }

    @GetMapping("/files/instance/{file}")
    public ResponseEntity<byte[]> getInstanceFile(@PathVariable String file) throws IOException {
        try (var is = FilesController.class.getResourceAsStream("/static/moth/cyber-moth-avatar.png")) {
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(is.readAllBytes());
        }
    }

    @GetMapping("/files/users/{user}/{file}")
    public ResponseEntity<byte[]> getUserFile(@PathVariable String user, @PathVariable String file) throws IOException {
        try (var is = FilesController.class.getResourceAsStream("/static/moth/cyber-moth-avatar.png")) {
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(is.readAllBytes());
        }
    }

    @GetMapping("/favicon.ico")
    public ResponseEntity<byte[]> getFavicon() throws IOException {
        try (var is = FilesController.class.getResourceAsStream("/static/moth/favicon.png")) {
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(is.readAllBytes());
        }
    }
}
