package edu.sjsu.moth.server.service;

import edu.sjsu.moth.server.controller.MothController;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.db.PubKeyPair;
import edu.sjsu.moth.server.db.PubKeyPairRepository;
import edu.sjsu.moth.server.db.WebfingerAlias;
import edu.sjsu.moth.server.db.WebfingerRepository;
import edu.sjsu.moth.util.WebFingerUtils;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * this class manages the logic and DB access around account management.
 * controllers shouldn't be doing account management directly and this class
 * should not be generating views for controllers.
 */
@CommonsLog
@Configuration
public class AccountService {
    @Autowired
    WebfingerRepository webfingerRepository;
    @Autowired
    AccountRepository accountRepository;

    @Autowired
    PubKeyPairRepository pubKeyPairRepository;

    @Autowired
    EmailService emailService;

    static PubKeyPair genPubKeyPair(String acct) {
        var pair = WebFingerUtils.genPubPrivKeyPem();
        return new PubKeyPair(acct, pair.pubKey(), pair.privKey());
    }

    public Mono<Void> createAccount(String username, String email, String password) {
        // there are some ugly race conditions and failure cases here!!!
        log.info("creating %s for %s\n".formatted(username, email));
        return accountRepository.findItemByAcct(username)
                .flatMap(a -> Mono.error(EmailService.AlreadyRegistered::new))
                .then(emailService.assignAccountToEmail(email, username, password))
                .then(accountRepository.save(new Account(username)))
                .then(webfingerRepository.save(new WebfingerAlias(username, username, MothController.HOSTNAME)))
                .then(pubKeyPairRepository.save(genPubKeyPair(username)))
                .then();
    }

    public Mono<Account> getAccount(String username) {
        return accountRepository.findItemByAcct(username);
    }

    public Mono<Account> getAccountById(String id) {
        return accountRepository.findById(id);
    }

    public Mono<String> getPublicKey(String id, boolean addIfMissing) {
        var mono = pubKeyPairRepository.findItemByAcct(id).map(pair -> pair.publicKeyPEM);
        if (addIfMissing) {
            mono = mono.switchIfEmpty(Mono.just(WebFingerUtils.genPubPrivKeyPem())
                                              .flatMap(p -> pubKeyPairRepository.save(
                                                      new PubKeyPair(id, p.pubKey(), p.privKey())))
                                              .map(p -> p.publicKeyPEM));
        }
        return mono;
    }

    public Mono<Account> updateAccount(Account a) {
        return accountRepository.save(a);
    }
}
