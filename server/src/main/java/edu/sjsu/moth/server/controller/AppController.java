package edu.sjsu.moth.server.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.sjsu.moth.server.db.*;
import edu.sjsu.moth.server.util.Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * This code handles first contact and oauth outh with the client.
 * The flow seems to be:
 * 1) get host-meta to get finger info
 * 2) do a webfinger (for fun?)
 * 3) call /api/vi/instance
 * 4) call /api/v1/apps to get client, id and secret
 * for signup
 * 5) call /oauth/token with client id & secret from 3) and grant_type set to client_credentials
 * 6) call /api/v1/accounts with the username, email, and password collected for signup
 * for login
 * 5) call /oauth/authorize with client id, secret and redirect this will return HTML
 * 6) the client will interact with the HTML until it redirects back to the application with a code (in /oauth/login)
 * 7) call /oauth/token with client id & secret from 3) and grant_type set to code
 * 8) call /api/v1/accounts/verify
 */
// spec found in https://docs.joinmastodon.org/methods/apps/
@RestController
public class AppController {
    public static final RegistrationException ERR_TAKEN = new RegistrationException("Username or email already taken",
                                                                                    Map.of("username",
                                                                                           List.of(Map.of("error",
                                                                                                          "ERR_TAKEN",
                                                                                                          "description",
                                                                                                          "username or email already taken"))));
    static private final Logger LOG = Logger.getLogger(AppController.class.getName());
    final static private Random nonceRandom = new SecureRandom();
    // from config file
    static String VAPID_KEY = "";
    @Autowired
    UserPasswordRepository userPasswordRepository;
    @Autowired
    WebfingerRepository webfingerRepository;
    /*
     * i think we may be able to get away with making these memory only since they are only
     * temporarily cached in memory. i'm not sure how big of a deal it is if they get lost on
     * restart.
     */ AtomicInteger appCounter = new AtomicInteger();
    ConcurrentHashMap<String, AppRegistrationEntry> registrations = new ConcurrentHashMap<>();

    @Autowired
    TokenRepository tokenRepository;

    /**
     * base64 URL encode a nonce of byteCount random bytes.
     */
    static String genNonce(int byteCount) {
        byte[] bytes = new byte[byteCount];
        nonceRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().encodeToString(bytes);
    }

    private Mono<String> generateAccessToken(String username) {
        //generate access token for user
        //need to put these in a database to map them to a user
        final var token = genNonce(33);
        return tokenRepository.save(new Token(token, username, LocalDateTime.now())).thenReturn(token);
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

    //https://docs.joinmastodon.org/methods/accounts/#create
    @PostMapping("/api/v1/accounts")
    public Mono<ResponseEntity<Object>> registerAccount(@RequestHeader("Authorization") String authorization,
                                                        @RequestBody RegistrationRequest request) throws RegistrationException, RateLimitException {
        //validate authorization header with bearer token authentication
        //authorization token has to be valid before registering
        MastodonRegistration.validateRegistrationRequest(request);
        //TODO: send confirmation email to the user
        //generate and return TokenResponse with access token
        return userPasswordRepository.findItemByUser(request.username)
                .map(userPassword -> userPassword == null ? Mono.error(ERR_TAKEN) : userPasswordRepository.save(
                        new UserPassword(request.username, request.password))).then(webfingerRepository.save(
                        new WebfingerAlias(request.username, request.username, MothController.HOSTNAME)))
                .then(generateAccessToken(request.username))
                .map(token -> ResponseEntity.ok(new TokenResponse(token, "*")));
    }

    @PostMapping("/api/v1/apps")
    ResponseEntity<Object> postApps(@RequestBody AppsRequest req) {
        var registration = new AppRegistration(appCounter.getAndIncrement(), req.client_name, req.redirect_uris,
                                               req.website, genNonce(33), genNonce(33), VAPID_KEY);
        // we should have a scheduled thread to clean up expired registrations, but for now we will do it on the fly
        checkExpirations();
        registrations.put(registration.client_id,
                          new AppRegistrationEntry(registration, LocalDateTime.now(), req.scopes));
        LOG.fine("postApps returning " + registration);
        return ResponseEntity.ok(registration);
    }

    @Autowired
    AccountRepository accountRepository;
    //to verify using the user token and retrieve credential account
    //source: https://docs.joinmastodon.org/methods/accounts/#verify_credentials
    @GetMapping("/api/v1/accounts/verify_credentials")
    public Mono<ResponseEntity<Object>> verifyCredentials(Principal user, @RequestHeader("Authorization") String authorizationHeader) {
        if (user != null) {
            return accountRepository.findItemByAcct(user.getName()).map(ResponseEntity::ok);
        } else {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
    }

    @GetMapping("/oauth/authorize")
    String getOauthAuthorize(@RequestParam String client_id, @RequestParam String redirect_uri,
                             @RequestParam(required = false, defaultValue = "") String error) throws IOException {
        try (var is = AppController.class.getResourceAsStream("/static/oauth/authorize.html")) {
            var authorizePage = new String(is.readAllBytes());
            authorizePage = authorizePage.replace("client_id_value", client_id);
            authorizePage = authorizePage.replace("redirect_uri_value", redirect_uri);
            authorizePage = authorizePage.replace("authorize_error", error);
            //noinspection ReassignedVariable
            return authorizePage;
        }
    }

    @GetMapping("/oauth/login")
    Mono<ResponseEntity<String>> getOauthLogin(@RequestParam String client_id, @RequestParam String redirect_uri,
                                               @RequestParam String user, @RequestParam String password) {
        return userPasswordRepository.findItemByUser(user).flatMap(userPassword -> {
            try {
                URI uri;
                if (userPassword == null) {
                    uri = new URI(
                            "/oauth/authorize?client_id=" + client_id + "&redirect_uri=" + redirect_uri + "&error=Bad"
                                    + "+user");
                } else if (!Util.checkPassword(password, userPassword.saltedPassword)) {
                    uri = new URI(
                            "/oauth/authorize?client_id=" + client_id + "&redirect_uri=" + redirect_uri + "&error=Bad"
                                    + "+password");
                } else {
                    var code = genNonce(33);
                    uri = new URI(redirect_uri + "?code=" + code);
                    return tokenRepository.save(new Token(code, user))
                            .thenReturn(ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT).location(uri).build());
                }
                return Mono.just(ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT).location(uri).build());
            } catch (URISyntaxException e) {
                throw new RuntimeException("Bad URI", e);
            }
        });
    }

    // implemented according to https://docs.joinmastodon.org/methods/oauth/#token
    @PostMapping("/oauth/token")
    ResponseEntity<Object> postOauthToken(@RequestBody TokenRequest req) {
        var registration = registrations.get(req.client_id);
        String code;
        String scopes;
        if (registration == null) {
            // this is probably the case of signing up a new user
            code = genNonce(33);
            scopes = req.scope;
        } else {
            if (!registration.registration.client_secret.equals(req.client_secret)) {
                throw new RuntimeException("bad client_secret");
            }
            code = registration.registration.client_secret;
            scopes = registration.scopes;
        }
        return ResponseEntity.ok(new TokenResponse(code, scopes));
    }

    record AppsRequest(String client_name, String redirect_uris, String scopes, String website) {}

    record TokenResponse(String access_token, String scope) {
        @JsonProperty("token_type")
        String getTokenType() {return "Bearer";}

        @JsonProperty("created_at")
        String getCreatedAt() {return Long.toString(System.currentTimeMillis() / 1000);}
    }

    record TokenRequest(String client_id, String client_secret, String code, String scope) {}

    record AppRegistration(int id, String name, String redirect_uri, String website, String client_id,
                           String client_secret, String vapid_key) {}

    record AppRegistrationEntry(AppRegistration registration, LocalDateTime createDate, String scopes) {}

    public static class RegistrationException extends RuntimeException {
        private final Object details;

        public RegistrationException(String message) {
            this(message, null);
        }

        public RegistrationException(String message, Object details) {
            super(message);
            this.details = details;
        }

        public Object getDetails() {
            return details;
        }
    }

    public static class RateLimitException extends RuntimeException {
        public RateLimitException(String message) {
            super(message);
        }
    }

    record RegistrationRequest(String username, String email, String password, boolean agreement, String locale,
                               String reason) {}

    record ErrorResponse(String error, Object details) {
        public ErrorResponse(String error) {
            this(error, null);
        }
    }

}
