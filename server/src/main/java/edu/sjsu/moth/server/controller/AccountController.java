package edu.sjsu.moth.server.controller;

import edu.sjsu.moth.generated.CredentialAccount;
import edu.sjsu.moth.generated.Relationship;
import edu.sjsu.moth.generated.Source;
import edu.sjsu.moth.server.annotations.RequestObject;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.AccountField;
import edu.sjsu.moth.server.db.Follow;
import edu.sjsu.moth.server.service.AccountService;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@CommonsLog
@RestController
public class AccountController {

    @Autowired
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PatchMapping(value = "/api/v1/accounts/update_credentials", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public Mono<ResponseEntity<Account>> updateCredentials(
            @RequestHeader("Authorization") String authorizationHeader, Principal user,
            @RequestBody Mono<MultiValueMap<String, Part>> parts) {

        return parts.flatMap(map -> {
            return accountService.getAccountById(user.getName()).flatMap(a -> {
                ArrayList<AccountField> fields = null;
                for (var entry : map.entrySet()) {
                    for (var p : entry.getValue()) {
                        if (p instanceof FormFieldPart ffp) {
                            var v = ffp.value();
                            String n = ffp.name();
                            switch (n) {
                                case "note" -> a.note = v;
                                case "header" -> a.header = v;
                                case "avatar" -> a.avatar = v;
                                case "locked" -> a.locked = v.equalsIgnoreCase("true");
                                case "bot" -> a.bot = v.equalsIgnoreCase("true");
                                case "discoverable" -> a.discoverable = v.equalsIgnoreCase("true");
                                default -> {
                                    if (n.startsWith("fields_attributes[")) {
                                        if (fields == null) {
                                            fields = new ArrayList<>();
                                        }
                                        var m = Pattern.compile("fields_attributes\\[(\\d+)\\]\\[(\\w+)\\]").matcher(n);
                                        if (m.find()) {
                                            var i = Integer.parseInt(m.group(1));
                                            // fill in the fields array until we get the index we need.
                                            // we should eventually see the rest of the values
                                            while (fields.size() <= i) {
                                                fields.add(new AccountField("", "", null));
                                            }
                                            var field = fields.get(i);
                                            switch (m.group(2)) {
                                                case "name" -> field.name = v;
                                                case "value" -> field.value = v;
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            System.out.println(p.name() + " " + p.getClass().getName());
                        }
                    }
                }
                if (fields != null) {
                    a.fields = fields;
                }
                return accountService.updateAccount(a);
            }).map(ResponseEntity::ok);
        });
    }

    @GetMapping("/api/v1/accounts/lookup")
    public Mono<ResponseEntity<Account>> lookUpAccount(@RequestParam String acct) {
        return accountService.getAccount(acct).map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    private CredentialAccount convertAccount2CredentialAccount(Account a) {
        var source = new Source("public", false, "", a.note, a.fields, 0);
        return new CredentialAccount(a.id, a.username, a.acct, a.display_name, a.locked, a.bot, a.created_at, a.note,
                                     a.url, a.avatar, a.avatar_static, a.header, a.header_static, a.followers_count,
                                     a.following_count, a.statuses_count, a.last_status_at, source, List.of(a.emojis),
                                     a.fields);
    }

    //to verify using the user token and retrieve credential account
    //source: https://docs.joinmastodon.org/methods/accounts/#verify_credentials
    //note that the CredentialAccount has the same info but a different format that Account :'(
    @GetMapping("/api/v1/accounts/verify_credentials")
    public Mono<ResponseEntity<CredentialAccount>> verifyCredentials(Principal user, @RequestHeader("Authorization")
    String authorizationHeader) {
        if (user != null) {
            return accountService.getAccount(user.getName()).map(this::convertAccount2CredentialAccount)
                    .map(ResponseEntity::ok).switchIfEmpty(
                            Mono.fromRunnable(() -> log.error("couldn't find " + user.getName())).then(Mono.empty()));
        } else {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
    }

    @GetMapping("/api/v1/accounts/relationships")
    public ResponseEntity<List<Relationship>> getApiV1AccountsRelationships(Principal user,
                                                                            @RequestObject RelationshipRequest req) {
        var relationships = new ArrayList<Relationship>();
        for (var i : req.id) {
            relationships.add(
                    new Relationship(i, false, false, false, false, false, false, false, false, false, false, false,
                                     false, ""));
        }
        return ResponseEntity.ok(relationships);
    }

    // spec: https://docs.joinmastodon.org/methods/accounts/#get
    // TODO at this point only local is implemented and no user checking is done.
    @GetMapping("/api/v1/accounts/{id}")
    public Mono<ResponseEntity<Account>> getApiV1AccountsById(Principal user, @PathVariable String id) {
        // it's not clear what we need to do with the user...
        return accountService.getAccount(id).map(ResponseEntity::ok);
    }

    // TODO: placeholder for testing
    @GetMapping("/api/v1/mutes")
    public Mono<ArrayList<Account>> getMutes(Integer max_id, Integer since_id, Integer limit) {

        return Mono.just(new ArrayList<Account>());
    }

    // TODO: placeholder for testing
    @GetMapping("/api/v1/blocks")
    public Mono<ArrayList<Account>> getBlocks(Integer max_id, Integer since_id, Integer min_id, Integer limit) {

        return Mono.just(new ArrayList<Account>());
    }


    @PostMapping("/api/v1/accounts/{id}/follow")
    public Mono<ResponseEntity<Follow>> followUser(@PathVariable("id") String followedId, Principal user){
        return accountService.getAccountById(user.getName()).flatMap(a -> accountService.saveFollow(a.id, followedId))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/api/v1/accounts/{id}/following")
    public Mono<InboxController.UsersFollowResponse> userFollowing(@PathVariable String id,
                                                                   @RequestParam(required = false) Integer page,
                                                                   @RequestParam(required = false) Integer limit) {
        return accountService.usersFollow(id, page, limit, "following");
    }


    private static class RelationshipRequest {
        public String[] id;
        public Boolean with_suspended;
    }

}
