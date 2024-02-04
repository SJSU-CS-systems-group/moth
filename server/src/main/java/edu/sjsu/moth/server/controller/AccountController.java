package edu.sjsu.moth.server.controller;

import edu.sjsu.moth.generated.CredentialAccount;
import edu.sjsu.moth.generated.Relationship;
import edu.sjsu.moth.generated.Source;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.AccountField;
import edu.sjsu.moth.server.service.AccountService;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@CommonsLog
@RestController
public class AccountController {

    @Autowired
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PatchMapping(value = "/api/v1/accounts/update_credentials", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public Mono<ResponseEntity<Account>> updateCredentials(@RequestHeader("Authorization") String authorizationHeader
            , Principal user, @RequestPart(required = false) String display_name,
                                                           @RequestPart(required = false) String note,
                                                           @RequestPart(required = false) String avatar,
                                                           @RequestPart(required = false) String header,
                                                           @RequestPart(required = false) String locked,
                                                           @RequestPart(required = false) String bot,
                                                           @RequestPart(required = false) String discoverable,
                                                           @RequestPart(required = false) List<AccountField> fields_attribute) {

        return accountService.getAccountById(user.getName()).flatMap(a -> {
            if (display_name != null) {
                a.display_name = display_name;
            }

            if (note != null) {
                a.note = note;
            }

            if (header != null) {
                a.header = header;
            }

            if (avatar != null) {
                a.avatar = avatar;
            }

            if (fields_attribute != null) {
                a.fields = fields_attribute;
            }
            if (locked != null) {
                a.locked = locked.equalsIgnoreCase("true");
            }

            if (bot != null) {
                a.bot = bot.equalsIgnoreCase("true");
            }

            if (discoverable != null) {
                a.discoverable = discoverable.equalsIgnoreCase("true");
            }

            return accountService.updateAccount(a);

        }).map(ResponseEntity::ok);

    }

    @GetMapping("/api/v1/accounts/lookup")
    public Mono<ResponseEntity<Account>> lookUpAccount(@RequestParam String acct) {
        return accountService.getAccount(acct)
                .map(ResponseEntity::ok)
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

    @GetMapping("/api/v1/accounts/relationships")
    public ResponseEntity<List<Relationship>> getApiV1AccountsRelationships(Principal user, String[] id,
                                                                            @RequestParam(required = false,
                                                                                    defaultValue = "false") boolean with_suspended) {
        var relationships = new ArrayList<Relationship>();
        for (var i : id) {
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

}
