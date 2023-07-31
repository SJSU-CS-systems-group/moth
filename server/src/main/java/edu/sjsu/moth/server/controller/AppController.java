package edu.sjsu.moth.server.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.sjsu.moth.generated.CredentialAccount;
import edu.sjsu.moth.generated.Source;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.Token;
import edu.sjsu.moth.server.db.TokenRepository;
import edu.sjsu.moth.server.service.AccountService;
import edu.sjsu.moth.server.service.EmailService;
import edu.sjsu.moth.server.util.MothConfiguration;
import edu.sjsu.moth.server.util.Util;
import edu.sjsu.moth.server.util.Util.TTLHashMap;
import lombok.Getter;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static edu.sjsu.moth.server.controller.AppController.ExceptionMessageFormat.emf;
import static edu.sjsu.moth.server.controller.i18nController.getExceptionMessage;

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
    final public List<ExceptionMessageFormat> emfList = List.of(
            emf(EmailService.BadCodeException.class, "AppBadCode", List.of(registrationEmail())),
            emf(EmailService.RegistrationNotFound.class, "AppRegistrationNotFound", List.of(registrationEmail())));
    private final TTLHashMap<String, String> code2Email = new TTLHashMap<>(10, TimeUnit.MINUTES);
    @Autowired
    AccountService accountService;

    @Autowired
    EmailService emailService;

    /*
     * i think we may be able to get away with making these memory only since they are only
     * temporarily cached in memory. i'm not sure how big of a deal it is if they get lost on
     * restart.
     */ AtomicInteger appCounter = new AtomicInteger();
    TTLHashMap<String, AppRegistrationEntry> registrations = new TTLHashMap<>(10, TimeUnit.MINUTES);
    @Autowired
    TokenRepository tokenRepository;
    @Autowired
    MessageSource messageSource;
    // required for i18n
    @Autowired
    private SpringTemplateEngine templateEngine;

    /**
     * base64 URL encode a nonce of byteCount random bytes.
     */
    static String genNonce(int byteCount) {
        byte[] bytes = new byte[byteCount];
        nonceRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().encodeToString(bytes);
    }

    public static String registrationEmail() {
        return "moth@" + MothConfiguration.mothConfiguration.getServerName();
    }

    private Mono<Token> generateAccessToken(String username, String email, String appName, String appWebsite) {
        //generate access token for user
        //need to put these in a database to map them to a user
        final var token = genNonce(33);
        return tokenRepository.save(new Token(token, username, email, appName, appWebsite, LocalDateTime.now()));
    }

    //https://docs.joinmastodon.org/methods/accounts/#create
    @PostMapping("/api/v1/accounts")
    public Mono<ResponseEntity<Object>> registerAccount(@RequestHeader("Authorization") String authorization,
                                                        @RequestBody RegistrationRequest request, Locale locale) throws RegistrationException, RateLimitException {
        var appName = "";
        var appWebsite = "";

        //validate authorization header with bearer token authentication
        //authorization token has to be valid before registering
        MastodonRegistration.validateRegistrationRequest(request);
        //TODO: send confirmation email to the user
        //generate and return TokenResponse with access token
        return accountService.getAccount(request.username)
                .flatMap(a -> Mono.error(ERR_TAKEN))
                .then(accountService.createAccount(request.username, request.email, request.password))
                .then(generateAccessToken(request.username, request.email, appName, appWebsite))
                .map(token -> ResponseEntity.ok((Object) new TokenResponse(token.token, "*")))
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                                      .body(new ErrorResponse(error2Message(e, locale)))));
    }

    public String error2Message(Throwable e, Locale locale) {
        for (var emf : emfList) {
            if (emf.exception.isInstance(e)) {
                return messageSource.getMessage(emf.code, emf.args.toArray(), locale);
            }
        }
        return e.getLocalizedMessage();
    }

    @GetMapping("/api/v1/accounts/lookup")
    public Mono<ResponseEntity<Account>> lookUpAccount(@RequestParam String acct) {
        System.out.println(acct);
        return accountService.getAccount(acct)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
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

    // spec: https://docs.joinmastodon.org/methods/accounts/#get
    // TODO at this point only local is implemented and no user checking is done.
    @GetMapping("/api/v1/accounts/{id}")
    public Mono<ResponseEntity<Account>> getApiV1AccountsById(Principal user, @PathVariable String id) {
        // it's not clear what we need to do with the user...
        return accountService.getAccount(id).map(ResponseEntity::ok);
    }

    private CredentialAccount convertAccount2CredentialAccount(Account a) {
        var source = new Source("public", false, "", a.note, a.fields, 0);
        return new CredentialAccount(a.id, a.username, a.acct, a.display_name, a.locked, a.bot, a.created_at, a.note,
                                     a.url, a.avatar, a.avatar_static, a.header, a.header_static, a.followers_count,
                                     a.following_count, a.statuses_count, a.last_status_at, source, List.of(a.emojis),
                                     a.fields);
    }

    @GetMapping("/oauth/authorize")
    public String getOauthAuthorize(@RequestParam String client_id, @RequestParam String redirect_uri,
                                    @RequestParam(required = false) String error) {
        // resolves locale to user locale; resolves the locale based on the "Accept-Language" header in the
        // request packet. resolved via the WebFilterChain.
        Context context = new Context(LocaleContextHolder.getLocale());
        context.setVariable("clientId", client_id);
        context.setVariable("redirectUri", redirect_uri);
        context.setVariable("authorizeError", error == null ? "" : error);
        return templateEngine.process("authorize", context);
    }

    @GetMapping("/oauth/login")
    Mono<ResponseEntity<String>> getOauthLogin(@RequestParam String client_id, @RequestParam String redirect_uri,
                                               @RequestParam String user, @RequestParam String password,
                                               Locale locale) {
        // the parameter comes in as "user" but it's actually the email
        return emailService.checkEmailCode(user, password)
                .then(Mono.fromCallable(() -> {
                    var code = genNonce(33);
                    code2Email.put(code, user);
                    return code;
                }))
                .flatMap(code -> Util.getMonoURI(redirect_uri + "?code=" + code))
                .onErrorResume(t -> Util.getMonoURI(
                        LOGIN_ERR_FORMAT.formatted(client_id, redirect_uri, Util.URLencode(error2Message(t, locale)))))
                .map(uri -> ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT).location(uri).build());
    }

    // implemented according to https://docs.joinmastodon.org/methods/oauth/#token
    @PostMapping("/oauth/token")
    Mono<ResponseEntity<Object>> postOauthToken(@RequestBody TokenRequest req) {
        var registration = registrations.get(req.client_id);
        if (registration != null && !registration.registration.client_secret.equals(req.client_secret)) {
            throw new RuntimeException(getExceptionMessage("clientSecretException", LocaleContextHolder.getLocale()));
        }
        var scopes = registration == null ? req.scope() : registration.scopes;
        var name = registration == null ? "" : registration.registration.name;
        var website = registration == null ? "" : registration.registration.website;

        var email = req.code == null ? "" : code2Email.getOrDefault(req.code, "");
        // generate an empty token, we'll fill it in with a real user later
        return generateAccessToken(name, email, name, website).map(
                t -> ResponseEntity.ok(new TokenResponse(t.token, scopes)));
    }

    public record ExceptionMessageFormat(Class<? extends Throwable> exception, String code, List<String> args) {
        // shorthand for setting up the message table
        static ExceptionMessageFormat emf(Class<? extends Throwable> exception, String code, List<String> args) {
            return new ExceptionMessageFormat(exception, code, args);
        }
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

    @Getter
    public static class RegistrationException extends RuntimeException {
        private final Object details;

        public RegistrationException(String message) {
            this(message, null);
        }

        public RegistrationException(String message, Object details) {
            super(message);
            this.details = details;
        }

    }

    public static class RateLimitException extends RuntimeException {
        public RateLimitException(String message) {
            super(message);
        }
    }

    record RegistrationRequest(String username, String email, String password, boolean agreement, String locale,
                               String reason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ErrorResponse(String error, Object details) {
        public ErrorResponse(String error) {
            this(error, null);
        }
    }

}
