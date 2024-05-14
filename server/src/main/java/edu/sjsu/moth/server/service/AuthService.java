package edu.sjsu.moth.server.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.sjsu.moth.server.db.Token;
import edu.sjsu.moth.server.db.TokenRepository;
import edu.sjsu.moth.server.util.MothConfiguration;
import edu.sjsu.moth.server.util.Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class AuthService {
    final static private Random nonceRandom = new SecureRandom();
    static String VAPID_KEY = AuthService.genNonce(33);
    private final Util.TTLHashMap<String, String> code2Email = new Util.TTLHashMap<>(10, TimeUnit.MINUTES);
    @Autowired
    AccountService accountService;
    @Autowired
    EmailService emailService;
    /*
     * i think we may be able to get away with making these memory only since they are only
     * temporarily cached in memory. i'm not sure how big of a deal it is if they get lost on
     * restart.
     */ AtomicInteger appCounter = new AtomicInteger();
    Util.TTLHashMap<String, AppRegistrationEntry> registrations = new Util.TTLHashMap<>(10, TimeUnit.MINUTES);
    @Autowired
    TokenRepository tokenRepository;

    /**
     * base64 URL encode a nonce of byteCount random bytes.
     */
    public static String genNonce(int byteCount) {
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

    private Mono<Token> updateAccessToken(String token, String username, String email) {
        return tokenRepository.findItemByToken(token).flatMap(t -> {
            t.user = username;
            t.email = email;
            return tokenRepository.save(t);
        });
    }

    public Mono<Token> registerAccount(String token, String username, String email, String password) {
        return accountService.createAccount(username, email, password).then(updateAccessToken(token, username, email));
    }

    public Mono<AppRegistration> registerApp(String clientName, String redirectUris, String scopes, String website) {
        var registration =
                new AppRegistration(appCounter.getAndIncrement(), clientName, redirectUris, website, genNonce(33),
                                    genNonce(33), VAPID_KEY);
        // we should have a scheduled thread to clean up expired registrations, but for now we will do it on the fly
        registrations.put(registration.client_id, new AppRegistrationEntry(registration, LocalDateTime.now(), scopes));
        return Mono.just(registration);

    }

    /*
     * return the code that will map to the logged in user (if successful)
     */
    public Mono<Object> login(String user, String password, String clientId) {
        return emailService.checkEmailCode(user, password).then(Mono.fromCallable(() -> {
            var code = genNonce(33);
            code2Email.put(code, user);
            return code;
        }));
    }

    public Mono<TokenResponse> generateToken(String clientId, String clientSecret, String code, String scope) {
        var registration = registrations.get(clientId);
        if (registration == null) return Mono.error(new RuntimeException("bad client_id"));
        if (!registration.registration.client_secret.equals(clientSecret)) {
            return Mono.error(new RuntimeException("client_secret does not match client_id"));
        }
        var appname = registration.registration.name;
        var website = registration.registration.website;

        // if code is null, we will fill it in the email later
        var email = code == null ? null : code2Email.get(code);
        var mono = email == null ? generateAccessToken("", "", appname, website) :
                emailService.registrationForEmail(email).flatMap(
                        r -> generateAccessToken(r.username, email, registration.registration.name(),
                                                 registration.registration.website));
        return mono.map(t -> new TokenResponse(t.token, scope));
    }

    public record TokenResponse(String access_token, String scope) {
        @JsonProperty("token_type")
        String getTokenType() {return "Bearer";}

        @JsonProperty("created_at")
        String getCreatedAt() {return Long.toString(System.currentTimeMillis() / 1000);}
    }

    public record AppRegistration(int id, String name, String redirect_uri, String website, String client_id,
                                  String client_secret, String vapid_key) {}

    public record AppRegistrationEntry(AppRegistration registration, LocalDateTime createDate, String scopes) {}
}
