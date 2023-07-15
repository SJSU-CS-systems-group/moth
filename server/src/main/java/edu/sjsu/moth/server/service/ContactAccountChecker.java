package edu.sjsu.moth.server.service;

import edu.sjsu.moth.server.util.MothConfiguration;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

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

    @Override
    public void run(ApplicationArguments args) throws Exception {
        var contact = MothConfiguration.mothConfiguration.getAccountName();
        var contactAccount = accountService.getAccount(contact).block();
        if (contactAccount == null) {
            log.warn("❌ contact account %s not found. creating with defaults".formatted(contact));
            var randomBytes = new byte[9];
            new Random().nextBytes(randomBytes);
            var randomPassword = Base64.getEncoder().encodeToString(randomBytes);
            accountService.createAccount(contact, randomPassword).block();
        } else {
            log.info("✅ contact account %s found".formatted(contact));
        }
    }
}
