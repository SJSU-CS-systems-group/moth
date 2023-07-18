package edu.sjsu.moth.server.service;

import edu.sjsu.moth.server.controller.MothController;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.db.PubKeyPair;
import edu.sjsu.moth.server.db.PubKeyPairRepository;
import edu.sjsu.moth.server.db.UserPassword;
import edu.sjsu.moth.server.db.UserPasswordRepository;
import edu.sjsu.moth.server.db.WebfingerAlias;
import edu.sjsu.moth.server.db.WebfingerRepository;
import edu.sjsu.moth.server.util.Util;
import edu.sjsu.moth.util.WebFingerUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import javax.naming.AuthenticationException;

/**
 * this class manages the logic and DB access around account management.
 * controllers shouldn't be doing account management directly and this class
 * should not be generating views for controllers.
 */
@Configuration
public class AccountService {
    @Autowired
    UserPasswordRepository userPasswordRepository;
    @Autowired
    WebfingerRepository webfingerRepository;
    @Autowired
    AccountRepository accountRepository;

    @Autowired
    PubKeyPairRepository pubKeyPairRepository;

    public Mono<Void> createAccount(String username, String password) {
        var pubPriv = WebFingerUtils.genPubPrivKeyPem();
        return accountRepository.save(new Account(username))
                .then(userPasswordRepository.save(new UserPassword(username, Util.encodePassword(password))))
                .then(webfingerRepository.save(new WebfingerAlias(username, username, MothController.HOSTNAME)))
                .then(pubKeyPairRepository.save(new PubKeyPair(username, pubPriv.pubKey(), pubPriv.privKey())))
                .then();
    }

    public Mono<Account> getAccount(String username) {
        return accountRepository.findItemByAcct(username);
    }

    public Mono<Account> getAccountById(String id) {
        return accountRepository.findById(id);
    }

    public Mono<Void> checkPassword(String user, String password) {
        return userPasswordRepository.findItemByUser(user)
                .switchIfEmpty(Mono.error(new AuthenticationException(user + " not registered")))
                .flatMap(up -> Util.checkPassword(password, up.saltedPassword) ? Mono.empty() : Mono.error(
                        new AuthenticationException("bad password")));
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

}
