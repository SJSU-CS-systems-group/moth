package edu.sjsu.moth.server;

import edu.sjsu.moth.server.Instance.Registrations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
public class InstanceController {
    @GetMapping("/api/v1/instance")
    public ResponseEntity<Object> getInfo() {
        //MAYBE TEST IT WITH A CURL + REMBER TO PUT THE REAL HOST ETC IN THE "mastodon.social",
        //                "Mastodon",
        //                "4.0.0rc1",
        //                "source_url_value",
        //                "description_value", ETCCCCC

        // make api calls/retrieve data aka instance info
        Instance.Usage usage = new Instance.Usage(0, 0);
        Instance.Thumbnail thumbnail = new Instance.Thumbnail("", 0, 0);
        String[] languages = {};
        Instance.Configuration configuration = new Instance.Configuration(false);
        Instance.Registrations registrations = new Instance.Registrations(true);
        Instance.Contact contact = new Instance.Contact("", "");
        Instance.Rule[] rules = {};
        //^ from nested class to give initial values for the corresponding properties of the Instance class!

        Instance instance = new Instance(
                "mastodon.social",
                "Mastodon",
                "4.0.0rc1",
                "source_url_value",
                "description_value",
                usage,
                thumbnail,
                languages,
                configuration,
                registrations,
                contact,
                rules
        );
        return new ResponseEntity<>(instance, HttpStatus.OK); //serialized as JSON
    }


    //populate all the attributes
//        instance.setDomain("mastodon.social");
//        instance.setTitle("Mastodon");
//        instance.setVersion("4.0.0rc1");
    //sam's stub:
//    @GetMapping("/api/v1/instance")
//    Instance instance(){
//        String client_name = "testInstance";
//        String redirect_uris = "urn:ietf:wg:oauth:2.0:oob";
//        String user = "testUser";
//        String host = "testHost";
//        String resource = "testResource";
//        return new Instance(resource, client_name, redirect_uris, user, host);
//    }

//    @PostMapping("/api/v1/apps")
//    ResponseEntity<Object> postApps(@RequestParam String client_name, @RequestParam String redirect_uris,
//                                    @RequestParam String scopes, @RequestParam String website) {
//        var registration = new AppController.AppRegistration(appCounter.getAndIncrement(), client_name, redirect_uris, website,
//                                                             genNonce(33), genNonce(33), VAPID_KEY);
//        // we should have a scheduled thread to clean up expired registrations, but for now we will do it on the fly
//        checkExpirations();
//        registrations.put(registration.client_id, new AppController.AppRegistrationEntry(registration, LocalDateTime.now(), scopes));
//        LOG.fine("postApps returning " + registration);
//        return new ResponseEntity<>(registration, HttpStatus.OK);
//    }

}