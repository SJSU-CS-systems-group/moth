package edu.sjsu.moth.server.controller;

import org.springframework.stereotype.Component;

@Component
public class MastodonRegistration {

    public static void validateRegistrationRequest(AppController.RegistrationRequest request) {
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

    private static boolean isValidUsername(String username) {
        return username.length() > 3 && username.length() < 13 && username.matches("[a-zA-Z0-9_]+");
    }

    private static boolean isValidEmail(String email) {
        return email.matches("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    }

    private static boolean isValidPassword(String password) {
        return password.length() >= 8;
    }
}