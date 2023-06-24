package edu.sjsu.moth.server;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;

@Configuration
@Controller
public class FilesController implements WebMvcConfigurer {
    public static String userFileURL(String user, String file) {
        return MothController.BASE_URL + "/files/users/" + user + "/" + file;
    }

    public static String instanceFileURL(String file) {
        return MothController.BASE_URL + "/files/instance/" + file;
    }

    @GetMapping("/files/instance/{file}")
    public ResponseEntity<byte[]> getInstanceFile(@PathVariable String file) throws IOException {
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        try (var is = FilesController.class.getResourceAsStream("/static/moth/cyber-moth-avatar.png")) {
            return new ResponseEntity<>(is.readAllBytes(), headers, HttpStatus.OK);
        }
    }

    @GetMapping("/files/users/{user}/{file}")
    public ResponseEntity<byte[]> getUserFile(@PathVariable String user, @PathVariable String file) throws IOException {
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        try (var is = FilesController.class.getResourceAsStream("/static/moth/cyber-moth-avatar.png")) {
            return new ResponseEntity<>(is.readAllBytes(), headers, HttpStatus.OK);
        }
    }

    @GetMapping("/favicon.ico")
    public ResponseEntity<byte[]> getFavicon() throws IOException {
        var headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE);
        try (var is = FilesController.class.getResourceAsStream("/static/moth/favicon.png")) {
            return new ResponseEntity<>(is.readAllBytes(), headers, HttpStatus.OK);
        }
    }
}
