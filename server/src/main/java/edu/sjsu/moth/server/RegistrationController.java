package edu.sjsu.moth.server;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RegistrationController {

    @PostMapping("/api/v1/accounts")
    public ResponseEntity<Object> registerAccount(@RequestHeader("Authorization") String authorization,
                                                  @RequestBody RegistrationRequest request) {
        //validate authorization header with bearer token authentication
        //authorization token has to be valid before registering
        try {
            String scope = "*";
            //include scope param in the authorization header (oauth2)
            String authHeaderWithScope = authorization + ", scope=" + scope;
            String accessToken = MastodonRegistration.registerUser(request, authHeaderWithScope);
            //send confirmation email to the user (more work needed?)
            sendConfirmationEmail(request.getEmail());
            //generate and return TokenResponse with access token
            return ResponseEntity.ok(new TokenResponse(accessToken));
        } catch (RegistrationException e) {
            //handle registration exceptions
            return ResponseEntity.unprocessableEntity().body(new ErrorResponse(e.getMessage(), e.getDetails()));
        } catch (RateLimitException e) {
            //handle rate limiting exception
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            //handle other exceptions
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Registration failed"));
        }
    }

    private void sendConfirmationEmail(String email) {
        //add implementation for sending confirmation email here!!!!
    }

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

    record RegistrationRequest(String username, String email, String password, boolean agreement, String locale, String reason) {
        public String getEmail() {
            return email;
        }
    }

    record TokenResponse(String token) {
    }

    record ErrorResponse(String error, Object details) {
        public ErrorResponse(String error) {
            this(error, null);
        }
    }
}