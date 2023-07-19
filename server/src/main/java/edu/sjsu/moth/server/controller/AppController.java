package edu.sjsu.moth.server.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.sjsu.moth.generated.CredentialAccount;
import edu.sjsu.moth.generated.Source;
import edu.sjsu.moth.generated.Status;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.StatusRepository;
import edu.sjsu.moth.server.db.Token;
import edu.sjsu.moth.server.db.TokenRepository;
import edu.sjsu.moth.server.service.AccountService;
import edu.sjsu.moth.server.util.Util;
import edu.sjsu.moth.server.util.Util.TTLHashMap;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.security.Principal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
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

@CommonsLog
@RestController
public class AppController {
    public static final RegistrationException ERR_TAKEN = new RegistrationException("Username or email already taken",
                                                                                    Map.of("username",
                                                                                           List.of(Map.of("error",
                                                                                                          "ERR_TAKEN",
                                                                                                          "description",
                                                                                                          "username or email already taken"))));
    public static final String LOGIN_ERR_FORMAT = "/oauth/authorize?client_id=%s&redirect_uri=%s&error=%s";
    static private final Logger LOG = Logger.getLogger(AppController.class.getName());
    final static private Random nonceRandom = new SecureRandom();
    // from config file
    static String VAPID_KEY = genNonce(33);
    private final TTLHashMap<String, String> code2User = new TTLHashMap<>(10, TimeUnit.MINUTES);
    @Autowired
    AccountService accountService;
    /*
     * i think we may be able to get away with making these memory only since they are only
     * temporarily cached in memory. i'm not sure how big of a deal it is if they get lost on
     * restart.
     */ AtomicInteger appCounter = new AtomicInteger();
    TTLHashMap<String, AppRegistrationEntry> registrations = new TTLHashMap<>(10, TimeUnit.MINUTES);
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

    private Mono<Token> generateAccessToken(String username, String appName, String appWebsite) {
        //generate access token for user
        //need to put these in a database to map them to a user
        final var token = genNonce(33);
        return tokenRepository.save(new Token(token, username, appName, appWebsite, LocalDateTime.now()));
    }

    //https://docs.joinmastodon.org/methods/accounts/#create
    @PostMapping("/api/v1/accounts")
    public Mono<ResponseEntity<Object>> registerAccount(@RequestHeader("Authorization") String authorization,
                                                        @RequestBody RegistrationRequest request) throws RegistrationException, RateLimitException {
        var appName = "";
        var appWebsite = "";

        //validate authorization header with bearer token authentication
        //authorization token has to be valid before registering
        MastodonRegistration.validateRegistrationRequest(request);
        //TODO: send confirmation email to the user
        //generate and return TokenResponse with access token
        return accountService.getAccount(request.username)
                .flatMap(a -> Mono.error(ERR_TAKEN))
                .then(accountService.createAccount(request.username, request.password))
                .then(generateAccessToken(request.username, appName, appWebsite))
                .map(token -> ResponseEntity.ok(new TokenResponse(token.token, "*")));
    }

    @PostMapping("/api/v1/emails/confirmations")
    String emailsConfirmations() {
        // we don't use email verification... YET!
        return "{}";
    }

    @PostMapping("/api/v1/apps")
    ResponseEntity<Object> postApps(@RequestBody AppsRequest req) {
        var registration = new AppRegistration(appCounter.getAndIncrement(), req.client_name, req.redirect_uris,
                                               req.website, genNonce(33), genNonce(33), VAPID_KEY);
        // we should have a scheduled thread to clean up expired registrations, but for now we will do it on the fly
        registrations.put(registration.client_id,
                          new AppRegistrationEntry(registration, LocalDateTime.now(), req.scopes));
        LOG.fine("postApps returning " + registration);
        return ResponseEntity.ok(registration);
    }

    //to verify using the user token and retrieve credential account
    //source: https://docs.joinmastodon.org/methods/accounts/#verify_credentials
    //note that the CredentialAccount has the same info but a different format that Account :'(
    @GetMapping("/api/v1/accounts/verify_credentials")
    public Mono<ResponseEntity<CredentialAccount>> verifyCredentials(Principal user,
                                                                     @RequestHeader("Authorization") String authorizationHeader) {
        if (user != null) {
            return accountService.getAccount(user.getName())
                    .map(this::convertAccount2CredentialAccount)
                    .map(ResponseEntity::ok)
                    .switchIfEmpty(
                            Mono.fromRunnable(() -> log.error("couldn't find " + user.getName())).then(Mono.empty()));
        } else {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
    }

    private CredentialAccount convertAccount2CredentialAccount(Account a) {
        var source = new Source("public", false, "", a.note, List.of(a.fields), 0);
        return new CredentialAccount(a.id, a.username, a.acct, a.display_name, a.locked, a.bot, a.created_at, a.note,
                                     a.url, a.avatar, a.avatar_static, a.header, a.header_static, a.followers_count,
                                     a.following_count, a.statuses_count, a.last_status_at, source, List.of(a.emojis),
                                     List.of(a.fields));
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
        return accountService.checkPassword(user, password)
                .then(Mono.fromCallable(() -> {
                    var code = genNonce(33);
                    code2User.put(code, user);
                    return code;
                }))
                .flatMap(code -> Util.getMonoURI(redirect_uri + "?code=" + code))
                .onErrorResume(t -> Util.getMonoURI(
                        LOGIN_ERR_FORMAT.formatted(client_id, redirect_uri, Util.URLencode(t.getMessage()))))
                .map(uri -> ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT).location(uri).build());
    }

    // implemented according to https://docs.joinmastodon.org/methods/oauth/#token
    @PostMapping("/oauth/token")
    Mono<ResponseEntity<Object>> postOauthToken(@RequestBody TokenRequest req) {
        var registration = registrations.get(req.client_id);
        String scopes;
        String name = "";
        String website = "";
        if (registration == null) {
            scopes = req.scope;
        } else {
            if (!registration.registration.client_secret.equals(req.client_secret)) {
                throw new RuntimeException("bad client_secret");
            }
            scopes = registration.scopes;
            name = registration.registration.name;
            website = registration.registration.website;
        }
        var user = req.code == null ? "" : code2User.getOrDefault(req.code, "");
        // generate an empty token, we'll fill it in with a real user later
        return generateAccessToken(user, name, website).map(t -> ResponseEntity.ok(new TokenResponse(t.token, scopes)));
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
