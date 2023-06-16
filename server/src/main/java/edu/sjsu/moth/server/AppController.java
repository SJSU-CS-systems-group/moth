package edu.sjsu.moth.server;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

// spec found in https://docs.joinmastodon.org/methods/apps/
@RestController
public class AppController {
    static private final Logger LOG = Logger.getLogger(AppController.class.getName());
    final static private Random nonceRandom = new SecureRandom();
    // from config file
    static String VAPID_KEY = "";
    /*
     * i think we may be able to get away with making these memory only since they are only
     * temporarily cached in memory. i'm not sure how big of a deal it is if they get lost on
     * restart.
     */ AtomicInteger appCounter = new AtomicInteger();
    ConcurrentHashMap<String, AppRegistrationEntry> registrations = new ConcurrentHashMap<>();

    /**
     * base64 URL encode a nonce of byteCount random bytes.
     */
    static private String genNonce(int byteCount) {
        byte[] bytes = new byte[byteCount];
        nonceRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().encodeToString(bytes);
    }

    // Expire everything older than 10 minutes
    void checkExpirations() {
        var toDelete = new ArrayList<String>();
        var expireTime = LocalDateTime.now().minusMinutes(10);
        registrations.forEach((k, v) -> {
            if (v.createDate.isBefore(expireTime)) toDelete.add(k);
        });
        toDelete.forEach(k -> registrations.remove(k));
    }

    @PostMapping("/api/v1/apps")
    ResponseEntity<Object> postApps(@RequestParam String client_name, @RequestParam String redirect_uris,
                                    @RequestParam String scopes, @RequestParam String website) {
        var registration = new AppRegistration(appCounter.getAndIncrement(), client_name, redirect_uris, website,
                                               genNonce(33), genNonce(33), VAPID_KEY);
        // we should have a scheduled thread to clean up expired registrations, but for now we will do it on the fly
        checkExpirations();
        registrations.put(registration.client_id, new AppRegistrationEntry(registration, LocalDateTime.now(), scopes));
        LOG.fine("postApps returning " + registration);
        return new ResponseEntity<>(registration, HttpStatus.OK);
    }

    record AppRegistration(int id, String name, String redirect_uri, String website, String client_id,
                           String client_secret, String vapid_key) {}

    record AppRegistrationEntry(AppRegistration registration, LocalDateTime createDate, String scopes) {}
}
