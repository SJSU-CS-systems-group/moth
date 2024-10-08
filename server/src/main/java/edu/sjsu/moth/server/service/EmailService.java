package edu.sjsu.moth.server.service;

import edu.sjsu.moth.server.db.EmailRegistration;
import edu.sjsu.moth.server.db.EmailRegistrationRepository;
import edu.sjsu.moth.server.db.PubKeyPairRepository;
import edu.sjsu.moth.server.util.MothConfiguration;
import edu.sjsu.moth.server.util.Util;
import edu.sjsu.moth.util.EmailCodeUtils;
import lombok.extern.apachecommons.CommonsLog;
import net.mailific.server.MailObject;
import net.mailific.server.ServerConfig;
import net.mailific.server.commands.Connect;
import net.mailific.server.commands.Data;
import net.mailific.server.commands.Ehlo;
import net.mailific.server.commands.Helo;
import net.mailific.server.commands.Mail;
import net.mailific.server.commands.Noop;
import net.mailific.server.commands.Quit;
import net.mailific.server.commands.Rcpt;
import net.mailific.server.commands.Rset;
import net.mailific.server.extension.EightBitMime;
import net.mailific.server.extension.Pipelining;
import net.mailific.server.extension.SmtpUtf8;
import net.mailific.server.netty.NettySmtpServer;
import net.mailific.server.reference.BaseMailObject;
import net.mailific.server.reference.BaseMailObjectFactory;
import net.mailific.server.session.Reply;
import net.mailific.server.session.SmtpSession;
import org.jetbrains.annotations.NotNull;
import org.simplejavamail.api.email.EmailPopulatingBuilder;
import org.simplejavamail.api.email.config.DkimConfig;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.api.mailer.config.TransportStrategy;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.MailerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * Register an email service to set up new account based on emails.
 * <p>
 * This assumes some infrastructure has been set up:
 * 1) to receive mail we need nginx to do the SPF checking and the TLS. ngnix will forward the SMTP requests to us.
 * 2) we are using a mail relay that's going to let us send mail, it will need to have the SPF records set up. we
 * aren't using TLS to connect to that either, so it should be over a trusted network.
 */
@CommonsLog
@Configuration
public class EmailService implements ApplicationRunner {
    Mailer mailer;
    @Autowired
    EmailRegistrationRepository emailRegistrationRepository;

    // needed to get the DKIM private key
    @Autowired
    PubKeyPairRepository pubKeyPairRepository;

    @Autowired
    MessageSource messageSource;

    String domain;
    List<BiFunction<String, String, Boolean>> listeners = Collections.synchronizedList(new LinkedList<>());
    byte[] dkimPrivateKey;
    String dkimPublicKeyBase64;

    static public String extractEmail(String from) {
        var startBracket = from.lastIndexOf('<');
        // no brackets mean we just have an email address
        if (startBracket == -1) return from;
        var endBracket = from.lastIndexOf('>');
        if (endBracket < startBracket) throw new RuntimeException("badly formatted email: " + from);
        return from.substring(startBracket + 1, endBracket);
    }

    public static String unPEM(String pem) {
        return pem.replaceAll("--+[^-]+--+", "").replace("\n", "").replace("\r", "");
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        var dkimPubKeyPair = pubKeyPairRepository.findItemByAcct(PubKeyPairRepository.DKIM_ACCT_ID).block();
        if (dkimPubKeyPair == null) {
            dkimPubKeyPair =
                    pubKeyPairRepository.save(AccountService.genPubKeyPair(PubKeyPairRepository.DKIM_ACCT_ID)).block();
            if (dkimPubKeyPair == null) {
                log.error("❌ could not create DKIM public key");
                System.exit(2);
            }
            log.info("❗ created DKIM public key");
        }
        dkimPrivateKey = Base64.getDecoder().decode(unPEM(dkimPubKeyPair.privateKeyPEM));
        dkimPublicKeyBase64 = unPEM(dkimPubKeyPair.publicKeyPEM);
        log.info("✅ found DKIM public key: " + dkimPublicKeyBase64);

        var cfg = MothConfiguration.mothConfiguration;
        domain = cfg.getServerName();
        if (cfg.getSMTPLocalPort() == -1 || cfg.getSMTPServerHost() == null) {
            log.warn("SMTP configuration not fully present. skipping email set up.");
            return;
        }
        mailer = MailerBuilder.withDebugLogging(false).withSMTPServer(cfg.getSMTPServerHost(), cfg.getSMTPServerPort())
                .withTransportStrategy(TransportStrategy.SMTP).withProperty("mail.smtp.localhost", domain)
                .buildMailer();
        var myMailObjectFactory = new BaseMailObjectFactory() {
            @Override
            public MailObject newMailObject(SmtpSession session) {
                return new MyBaseMailObject();
            }
        };
        var serverConfig = ServerConfig.builder().withListenPort(cfg.getSMTPLocalPort()).withListenHost("::")
                .withCommandHandlers(
                        List.of(new Ehlo(domain), new Helo(domain), new Mail(myMailObjectFactory), new Rcpt(),
                                new Data(), new Quit(), new Rset(), new Noop()))
                .withExtensions(List.of(new EightBitMime(), new SmtpUtf8(), new Pipelining()))
                .withConnectHandler(new Connect(domain)).build();
        var server = new NettySmtpServer(serverConfig);
        server.start();
    }

    /**
     * throws BadCodeException password didn't match
     * throws RegistrationNotFound registration email not received
     *
     * @return username
     */
    public Mono<String> checkEmailCode(String email, String password) {
        if (MothConfiguration.mothConfiguration.getSMTPLocalPort() == -1) return Mono.empty();
        return emailRegistrationRepository.findById(EmailCodeUtils.normalizeEmail(email)).flatMap(reg -> {
            if (reg.username == null) return Mono.error(new RegistrationNotFound());
            if (EmailCodeUtils.checkPassword(password, reg.saltedPassword)) return Mono.just(reg.username);
            return Mono.error(new BadCodeException());
        }).switchIfEmpty(Mono.error(new RegistrationNotFound()));
    }

    /**
     * throws BadCodeException password didn't match
     * throws AlreadyRegistered email already assigned to a username
     * throws RegistrationNotFound registration email not received
     */
    public Mono<Void> assignAccountToEmail(String email, String username, String password) {
        // TODO: there does seem to be the potential for take over of accounts through this vector since we don't check
        //       that the username isn't already assigned to another email
        return emailRegistrationRepository.findById(EmailCodeUtils.normalizeEmail(email)).flatMap(r -> {
            if (!EmailCodeUtils.checkPassword(password, r.saltedPassword)) return Mono.error(new BadCodeException());
            if (r.username == null) {
                r.username = username;
                return emailRegistrationRepository.save(r);
            }
            return Mono.error(new AlreadyRegistered());
        }).switchIfEmpty(Mono.error(new RegistrationNotFound())).then();
    }

    public Mono<EmailRegistration> registerEmail(String email, String password) {
        var reg = new EmailRegistration();
        reg.id = EmailCodeUtils.normalizeEmail(email);
        reg.email = email;
        reg.lastEmail = reg.firstEmail = EmailCodeUtils.now();
        reg.saltedPassword = EmailCodeUtils.encodePassword(password);
        return emailRegistrationRepository.save(reg);
    }

    public Mono<EmailRegistration> registrationForEmail(String email) {
        return emailRegistrationRepository.findById(EmailCodeUtils.normalizeEmail(email));
    }

    public void listenForEmail(BiFunction<String, String, Boolean> receive) {
        listeners.add(receive);
    }

    public @NotNull CompletableFuture<Void> sendMail(EmailPopulatingBuilder emailBuilder) {
        var dkimCfg = DkimConfig.builder().dkimPrivateKeyData(dkimPrivateKey).dkimSelector("moth")
                .dkimSigningDomain(MothConfiguration.mothConfiguration.getServerName()).build();
        return mailer.sendMail(emailBuilder.buildEmail());
    }

    /**
     * indicates that a bad code/password was passed
     */
    public static class BadCodeException extends Exception {}

    /**
     * indicates that the email is not registered
     */
    public static class RegistrationNotFound extends Exception {}

    /**
     * indicates that the email is already registered
     */
    public static class AlreadyRegistered extends Exception {}

    /**
     * this class gleans to, from, subject, and message id to reply with a registration password.
     * it's very conservative and does not look at the body at all.
     */
    private class MyBaseMailObject extends BaseMailObject {
        String messageId;
        String to;
        String from;
        String subject;
        String firstLine = "";
        String lastLine = "";
        boolean blankSeen;
        boolean inReplyToSeen;

        @Override
        public void writeLine(byte[] lineBytes, int offset, int length) {
            var line = new String(lineBytes, offset, length);
            if (!blankSeen) {
                var parts = line.split(":", 2);
                switch (parts[0].toLowerCase()) {
                    case "from" -> from = parts[1].strip();
                    case "to" -> to = parts[1].strip();
                    case "subject" -> subject = parts[1].strip();
                    case "message-id" -> messageId = parts[1].strip();
                    case "in-reply-to" -> inReplyToSeen = true;
                    case "" -> blankSeen = true;
                }
            } else {
                if (firstLine.isEmpty()) firstLine = line;
                lastLine = line;
            }
        }

        @Override
        public Reply complete(SmtpSession session) {
            // only reply to fresh messages that don't talk about deamons or failures
            log.info("received mail from %s to %s about %s".formatted(from, to, subject));
            if (!to.contains(AuthService.registrationEmail())) {
                log.warn("ignoring mail from: %s, to: %s, subject: %s, body=%s..%s".formatted(from, to, subject,
                                                                                              firstLine, lastLine));

            }
            if (!inReplyToSeen && !from.contains("mailer-daemon") && !subject.contains("ailure")) {
                // this will notify all the listeners and remove them if they are done listening
                listeners.removeIf(f -> !f.apply(from, subject));
                var emailId = extractEmail(from);
                var eb = EmailBuilder.startingBlank().from(AuthService.registrationEmail()).to(from)
                        .withHeader("In-Reply-To", messageId).withHeader("References", messageId);
                var registration = subject.contains("regist");
                var reset = subject.contains("reset");
                var replySubject = "re: " + subject;
                Mono<EmailPopulatingBuilder> mono;
                if (registration == reset) {
                    mono = Mono.just(eb.withSubject(replySubject).withPlainText(getMessage("emailUnrecognizedReply")));
                } else {
                    mono = emailRegistrationRepository.findById(EmailCodeUtils.normalizeEmail(emailId)).flatMap(reg -> {
                        if (registration) {
                            eb.withSubject(replySubject).withPlainText(getMessage("emailAlreadyRegistered"));
                            return Mono.just(eb);
                        } else {
                            var password = Util.generatePassword();
                            reg.saltedPassword = EmailCodeUtils.encodePassword(password);
                            reg.lastEmail = EmailCodeUtils.now();
                            return emailRegistrationRepository.save(reg).thenReturn(
                                    eb.withSubject(getMessage("emailSubjectPasswordReset"))
                                            .withPlainText(getMessage("emailNewPassword", password)));
                        }
                    }).switchIfEmpty(Mono.defer(() -> {
                        if (registration) {
                            var password = Util.generatePassword();
                            return registerEmail(emailId, password).thenReturn(
                                    eb.withSubject(getMessage("emailSubjectWelcome", domain))
                                            .withPlainText(getMessage("emailNewPassword", password)));
                        } else {
                            eb.withSubject(replySubject).withPlainText(getMessage("emailNotRegistered"));
                            return Mono.just(eb);

                        }
                    }));
                }
                mono.doOnNext(email -> log.info("sending " + email.getSubject() + " to " + email.getRecipients()))
                        .map(EmailService.this::sendMail).block();
            }
            return super.complete(session);
        }

        @NotNull
        private String getMessage(String code, String... args) {
            return messageSource.getMessage(code, args, Locale.getDefault());
        }
    }
}