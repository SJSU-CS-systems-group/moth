package edu.sjsu.moth.server.controller;

import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.AccountField;
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.List;

@RestController
public class AccountController {

    private final AccountRepository accountRepository;

    private final AccountService accountService;

    // username
    // blocking?
    // set method for fields
    @Autowired
    public AccountController(AccountRepository accountRepository, AccountService accountService) {
        this.accountRepository = accountRepository;
        this.accountService = accountService;
    }

    @PatchMapping(value = "/api/v1/accounts/update_credentials", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public Mono<ResponseEntity<Account>> updateCredentials(@RequestHeader("Authorization") String authorizationHeader
            , Principal user, @RequestPart(required = false) String display_name,
                                                           @RequestPart(required = false) String note,
                                                           @RequestPart(required = false) String avatar,
                                                           @RequestPart(required = false) String header,
                                                           @RequestPart(required = false) Boolean locked,
                                                           @RequestPart(required = false) Boolean bot,
                                                           @RequestPart(required = false) Boolean discoverable,
                                                           @RequestPart(required = false) List<AccountField> fields_attribute) {

        //String username =

        return accountService.getAccountById(user.getName()).flatMap(a -> {
            System.out.println("before" + a.note);

            if (display_name != null) {
                a.setDisplayName(display_name);
            }

            if (note != null) {
                a.setNote(note);
            }

            if (header != null) {
                a.setHeader(header);
            }

            if (avatar != null) {
                a.setAvatar(avatar);
            }

            if (fields_attribute != null) {
                a.setFields(fields_attribute);
            }
            if (locked != null) {
                a.setLocked(locked);
            }

            if (bot != null) {
                a.setBot(bot);
            }

            if (discoverable != null) {
                a.setDiscoverable(discoverable);
            }

            System.out.println("After" + a.note);
            return accountService.updateAccount(a);

        }).map(ResponseEntity::ok);

    }

}
