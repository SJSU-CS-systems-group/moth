package edu.sjsu.moth.server;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InstanceController {
    public record Instance(String domain, String title, String version, String sourceUrl, String description,
                           Usage usage, Thumbnail thumbnail, String[] languages, Configuration configuration,
                           Registrations registrations, Contact contact, Rule[] rules) {

        public static record Usage(int posts, int users) {}

        public static record Thumbnail(String url, int width, int height) {}

        public static record Configuration(boolean supportsStreaming) {}

        public static record Registrations(boolean open) {}

        public static record Contact(String email, String phoneNumber) {}

        public static record Rule(String id, String text) {}
    }

    @GetMapping("/api/v1/instance")
    public ResponseEntity<Object> getInfo() {
        // make api calls/retrieve data aka instance info
        Instance.Usage usage = new Instance.Usage(0, 0);
        Instance.Thumbnail thumbnail = new Instance.Thumbnail("", 0, 0);
        String[] languages = {};
        Instance.Configuration configuration = new Instance.Configuration(false);
        Instance.Registrations registrations = new Instance.Registrations(true);
        Instance.Contact contact = new Instance.Contact("", "");
        Instance.Rule[] rules = {};
        //^from nested class to give initial values for the corresponding properties of the Instance class!

        Instance instance = new Instance(
                "cheah.homeofcode.com",
                "Moth",
                "4.0.0rc1",
                "https://github.com/mastodon/mastodon",
                "ANYTHINGGGGGGGG",
                usage,
                thumbnail,
                languages,
                configuration,
                registrations,
                contact,
                rules
        );
        return new ResponseEntity<>(instance, HttpStatus.OK); // serialized as JSON
    }
}
