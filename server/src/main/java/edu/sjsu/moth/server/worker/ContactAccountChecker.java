package edu.sjsu.moth.server.worker;

import edu.sjsu.moth.server.service.AccountService;
import edu.sjsu.moth.server.service.EmailService;
import edu.sjsu.moth.server.util.MothConfiguration;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.Random;

/**
 * make sure the contact account is set up. it is used in some of
 * the basic messages, so a lot of things are going to break if it
 * is not there.
 * <p>
 * if the contact account isn't present, we will create a basic one.
 */
@CommonsLog
@Configuration
public class ContactAccountChecker implements ApplicationRunner {
    @Autowired
    AccountService accountService;

    @Autowired
    EmailService emailService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        var contact = MothConfiguration.mothConfiguration.getAccountName();
        var contactAccount = accountService.getAccount(contact).block();
        if (contactAccount == null) {
            log.warn("❌ contact account %s not found. creating with defaults".formatted(contact));
            var randomBytes = new byte[9];
            new Random().nextBytes(randomBytes);
            var randomPassword = Base64.getEncoder().encodeToString(randomBytes);
            var contactEmail = MothConfiguration.mothConfiguration.getContactEmail();
            emailService.registerEmail(contactEmail, randomPassword)
                    // we are going to push ahead even if the email is already registered
                    .onErrorComplete()
                    .then(Mono.fromRunnable(() -> log.info("created registration for contact")))
                    .then(accountService.createAccount(contact, contactEmail, randomPassword))
                    .then(Mono.fromRunnable(() -> log.info("created account for contact")))
                    .block();
        } else {
            log.info("✅ contact account %s found".formatted(contact));
        }
    }
}
