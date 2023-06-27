package edu.sjsu.moth.server;

import org.springframework.stereotype.Component;

import static edu.sjsu.moth.server.AppController.genNonce;

@Component
public class MastodonRegistration {
    private static final int MAX_LENGTH = Integer.MAX_VALUE; //PLS CHANGE THE VALUE IF NEEDED!
    private static final int MIN_LENGTH = 1; //PLS CHANGE THE VALUE IF NEEDED!

    public static String registerUser(AppController.RegistrationRequest request) {
        //create a user account and return an access token (modify if need any specification/function)
        //validate request params
        validateRegistrationRequest(request);
        checkForErrorCases(request);
        String accessToken = generateAccessToken();
        //need to add webflux first before adding user and password to a database here
        //add code here: for confirmation email to the user?????
        return accessToken;
    }

    private static void validateRegistrationRequest(AppController.RegistrationRequest request) {
        if (!isValidUsername(request.username())) {
            throw new AppController.RegistrationException("Invalid username");
        }

        if (!isValidEmail(request.email())) {
            throw new AppController.RegistrationException("Invalid email");
        }

        if (!isValidPassword(request.password())) {
            throw new AppController.RegistrationException("Invalid password");
        }

        if (!request.agreement()) {
            throw new AppController.RegistrationException("Agreement must be accepted");
        }
    }

    private static void checkForErrorCases(AppController.RegistrationRequest request) {
        // Check for specific error cases
        if (isEmailBlocked(request.email())) {
            throw new AppController.RegistrationException("ERR_BLOCKED: E-mail provider is not allowed");
        }

        if (!isEmailReachable(request.email())) {
            throw new AppController.RegistrationException(
                    "ERR_UNREACHABLE: E-mail address does not resolve to any IP via DNS (MX, A, AAAA)");
        }

        if (isUsernameTaken(request.username())) {
            throw new AppController.RegistrationException("ERR_TAKEN: Username is already taken");
        }

        if (isUsernameReserved(request.username())) {
            throw new AppController.RegistrationException("ERR_RESERVED: Username is reserved");
        }
        if (isAgreementAccepted(request.agreement())) {
            throw new AppController.RegistrationException("ERR_ACCEPTED: Agreement has not been accepted");
        }
        if (isAttributeBlank(request.username())) {
            throw new AppController.RegistrationException("ERR_BLANK: Required attribute is blank");
        }
        if (isAttributeMalformed(request.username())) {
            throw new AppController.RegistrationException("ERR_INVALID: Attribute is malformed, e.g. wrong characters or invalid e-mail address");
        }
        if (isAttributeOverLimit(request.username())) {
            throw new AppController.RegistrationException("ERR_TOO_LONG: Attribute is over the character limit");
        }
        if (isAttributeUnderLimit(request.username())) {
            throw new AppController.RegistrationException("ERR_TOO_SHORT: Attribute is under the character requirement");
        }
        if (isAttributeAllowed(request.username())) {
            throw new AppController.RegistrationException("ERR_INCLUSION: Attribute is not one of the allowed values, e.g. unsupported locale");
        }

        //handle rate limiting
        if (isRateLimited()) {
            throw new AppController.RateLimitException("Too many requests");
        }
    }

    private static boolean isValidUsername(String username) {
        return username.matches("[a-zA-Z0-9_]+");
    }

    private static boolean isValidEmail(String email) {
        return email.matches("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    }

    private static boolean isValidPassword(String password) {
        return password.length() >= 8;
    }

    private static String generateAccessToken() {
        //generate access token for user
        //need to put these in a database to map them to a user?
        return genNonce(64);
    }

    //FURTHER MODIFICATION/IMPLEMENTATION REQUIRED IN THE METHODS BELOW!!!!!!!!!
    private static boolean isEmailBlocked(String email) {
        //check if the email provider is blocked
        return false;
    }

    private static boolean isEmailReachable(String email) {
        //check if the email address is reachable
        return true;
    }

    private static boolean isUsernameTaken(String username) {
        //check if the username is already taken
        return false;
    }

    private static boolean isUsernameReserved(String username) {
        //check if the username is reserved
        return false;
    }

    private static boolean isRateLimited() {
        //check if the registration process is rate limited
        return false;
    }

    private static boolean isAttributeAllowed(String attribute) {
        //check if the attribute contains only allowed characters
        return attribute.matches("[a-zA-Z0-9_]+");
    }

    private static boolean isAttributeUnderLimit(String attribute) {
        //check if the attribute is under the character requirement
        return attribute.length() < MIN_LENGTH;
    }

    private static boolean isAttributeOverLimit(String attribute) {
        //check if the attribute is over the character limit
        return attribute.length() > MAX_LENGTH;
    }

    private static boolean isAttributeMalformed(String attribute) {
        //check if the attribute is malformed
        //eg: check if wrong characters or invalid e-mail address
        return attribute.matches("[a-zA-Z0-9_]+");
    }

    private static boolean isAttributeBlank(String attribute) {
        //check if the attribute is blank
        return attribute.trim().isEmpty();
    }

    private static boolean isAgreementAccepted(boolean agreement) {
        //check if the agreement is accepted
        return agreement;
    }
}