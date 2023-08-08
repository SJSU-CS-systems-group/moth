package edu.sjsu.moth.server.worker;

import edu.sjsu.moth.server.service.AuthService;
import edu.sjsu.moth.server.service.EmailService;
import lombok.extern.apachecommons.CommonsLog;
import org.simplejavamail.email.EmailBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@CommonsLog
@Configuration
public class EmailServiceChecker implements ApplicationRunner {
    @Autowired
    EmailService emailService;

    Random rand = new Random();

    @Override
    public void run(ApplicationArguments args) {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            var randSubject = "mail check " + rand.nextLong();
            var latch = new CountDownLatch(1);
            emailService.listenForEmail((f, s) -> {
                if (s.equals(randSubject)) {
                    latch.countDown();
                    // if we get a match, we want to stop listening
                    return false;
                }
                return true;
            });
            emailService.sendMail(EmailBuilder.startingBlank()
                                          .to(AuthService.registrationEmail())
                                          .from(AuthService.registrationEmail())
                                          .withSubject(randSubject)
                                          .withPlainText("checking email"));
            var responseSeen = false;
            try {
                responseSeen = latch.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException ignore) {}
            if (responseSeen) log.info("✅ received test mail");
            else log.warn("❌ test mail not received. mail may not be working");
        }, 0, 30, TimeUnit.MINUTES);
    }
}
