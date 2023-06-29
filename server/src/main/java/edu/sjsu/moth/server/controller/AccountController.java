package edu.sjsu.moth.server.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AccountController {
    //to verify using the user token and retrieve credential account
    //source: https://docs.joinmastodon.org/methods/accounts/#verify_credentials
    @GetMapping("/api/v1/accounts/verify_credentials")
    public ResponseEntity<Object> verifyCredentials(@RequestHeader("Authorization") String authorizationHeader) {
        //get the user token from the authorization header
        String userToken = authorizationHeader.substring("Bearer ".length());
        CredentialAccount credentialAccount = getVerifiedCredentialAccount(userToken);
        if (credentialAccount != null) {
            return ResponseEntity.ok(credentialAccount);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    private CredentialAccount getVerifiedCredentialAccount(String userToken) {
        //call API to verify the credentials and retrieve the credential account
        //return verified credential account object or null if verification fail
        if (isValidToken(userToken)) { //if verified successfully
            AccountSource source = new AccountSource("public", false, "", "Sample note", null, 0);
            //note: the values below are from the example of the API doc!
            List<CredentialAccount.Emoji> emojis = List.of(new CredentialAccount.Emoji("fatyoshi", "https://example.com/fatyoshi.png", "https://example.com/fatyoshi.png", true));
            List<CredentialAccount.Field> fields = List.of(new CredentialAccount.Field("Website", "https://example.com", "2019-08-29T04:14:55.571+00:00"));
            return new CredentialAccount("14715", "trwnh", "trwnh", "infinite love ⴳ", false, false, "2016-11-24T10:02:12.085Z",
                                         "<p>Sample note</p>", "https://mastodon.social/@trwnh",
                                         "https://example.com/avatar.png", "https://example.com/avatar.png",
                                         "https://example.com/header.png", "https://example.com/header.png",
                                         821, 178, 33120, "2019-11-24T15:49:42.251Z",
                                         source, emojis, fields);
        }
        return null; //verification failed
    }

    private boolean isValidToken(String userToken) {
        //im not sure if this is how we want to implement token validation logic
        return userToken != null && !userToken.isEmpty(); //token is valid if its not empty or null
    }

    public record CredentialAccount(
            String id,
            String username,
            String acct,
            String display_name,
            boolean locked,
            boolean bot,
            String created_at,
            String note,
            String url,
            String avatar,
            String avatar_static,
            String header,
            String header_static,
            int followers_count,
            int following_count,
            int statuses_count,
            String last_status_at,
            AccountSource source,
            List<Emoji> emojis,
            List<Field> fields
    ) {

        public record Emoji(
                String shortcode,
                String url,
                String static_url,
                boolean visible_in_picker
        ) {
        }

        public record Field(
                String name,
                String value,
                String verified_at
        ) {
        }
    }

    public record AccountSource(
            String privacy,
            boolean sensitive,
            String language,
            String note,
            List<CredentialAccount.Field> fields,
            int follow_requests_count
    ) {
    }
}