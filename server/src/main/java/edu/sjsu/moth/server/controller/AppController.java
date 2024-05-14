package edu.sjsu.moth.server.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.sjsu.moth.server.service.AuthService;
import edu.sjsu.moth.server.service.EmailService;
import edu.sjsu.moth.server.util.Util;
import lombok.Getter;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;

import static edu.sjsu.moth.server.controller.AppController.ExceptionMessageFormat.emf;

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
    public static final String LOGIN_ERR_FORMAT = "/oauth/authorize?client_id=%s&redirect_uri=%s&error=%s";
    // from config file
    final public List<ExceptionMessageFormat> emfList =
            List.of(emf(EmailService.BadCodeException.class, "AppBadCode", List.of(AuthService.registrationEmail())),
                    emf(EmailService.RegistrationNotFound.class, "AppRegistrationNotFound",
                        List.of(AuthService.registrationEmail())));

    @Autowired
    AuthService authService;

    @Autowired
    MessageSource messageSource;
    // required for i18n
    @Autowired
    private SpringTemplateEngine templateEngine;

    //https://docs.joinmastodon.org/methods/accounts/#create
    @PostMapping("/api/v1/accounts")
    public Mono<ResponseEntity<Object>> registerAccount(
            @RequestHeader("Authorization") String authorization,
            @RequestBody RegistrationRequest request, Locale locale) throws RegistrationException, RateLimitException {
        //validate authorization header with bearer token authentication
        //authorization token has to be valid before registering
        MastodonRegistration.validateRegistrationRequest(request);
        //TODO: send confirmation email to the user
        //generate and return TokenResponse with access token
        var token = authorization.split(" ")[1]; // skip "Bearer"
        return authService.registerAccount(token, request.username, request.email, request.password)
                .then(Mono.just(ResponseEntity.ok((Object) new AuthService.TokenResponse(token, "*")))).onErrorResume(
                        e -> Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
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

    @PostMapping("/api/v1/emails/confirmations")
    ResponseEntity<String> emailsConfirmations() {
        return ResponseEntity.ok("{}");
    }

    @PostMapping("/api/v1/apps")
    Mono<ResponseEntity<AuthService.AppRegistration>> postApps(@RequestBody AppsRequest req) {
        return authService.registerApp(req.client_name, req.redirect_uris, req.scopes, req.website)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/oauth/authorize")
    public String getOauthAuthorize(
            @RequestParam String client_id,
            @RequestParam String redirect_uri, @RequestParam(required = false) String error) {
        // resolves locale to user locale; resolves the locale based on the "Accept-Language" header in the
        // request packet. resolved via the WebFilterChain.
        Context context = new Context(LocaleContextHolder.getLocale());
        context.setVariable("clientId", client_id);
        context.setVariable("redirectUri", redirect_uri);
        context.setVariable("authorizeError", error == null ? "" : error);
        return templateEngine.process("authorize", context);
    }

    @GetMapping("/oauth/login")
    Mono<ResponseEntity<String>> getOauthLogin(
            @RequestParam String client_id,
            @RequestParam String redirect_uri,
            @RequestParam String user, @RequestParam String password, Locale locale) {
        // the parameter comes in as "user" but it's actually the email
        return authService.login(user, password, client_id)
                .flatMap(code -> Util.getMonoURI(redirect_uri + "?code=" + code)).onErrorResume(t -> Util.getMonoURI(
                        LOGIN_ERR_FORMAT.formatted(client_id, redirect_uri, Util.URLencode(error2Message(t, locale)))))
                .map(uri -> ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT).location(uri).build());
    }

    // implemented according to https://docs.joinmastodon.org/methods/oauth/#token

    /**
     * This method will return a bearer token for subsequent requests.
     */
    @PostMapping("/oauth/token")
    Mono<ResponseEntity<AuthService.TokenResponse>> postOauthToken(@RequestBody TokenRequest req) {
        return authService.generateToken(req.client_id, req.client_secret, req.code, req.scope).map(ResponseEntity::ok);
    }

    public record ExceptionMessageFormat(Class<? extends Throwable> exception, String code, List<String> args) {
        // shorthand for setting up the message table
        static ExceptionMessageFormat emf(Class<? extends Throwable> exception, String code, List<String> args) {
            return new ExceptionMessageFormat(exception, code, args);
        }
    }

    record AppsRequest(String client_name, String redirect_uris, String scopes, String website) {}

    record TokenRequest(String client_id, String client_secret, String code, String scope) {}

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

    public record RegistrationRequest(String username, String email, String password, boolean agreement, String locale,
                                      String reason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ErrorResponse(String error, Object details) {
        public ErrorResponse(String error) {
            this(error, null);
        }
    }

}
