package edu.sjsu.moth.server.service;

import edu.sjsu.moth.server.db.EmailRegistration;
import edu.sjsu.moth.server.db.EmailRegistrationRepository;
import edu.sjsu.moth.server.util.MothConfiguration;
import edu.sjsu.moth.server.util.Util;
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
import org.simplejavamail.api.email.Email;
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

import java.util.List;
import java.util.Locale;

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

    @Autowired
    MessageSource messageSource;

    String domain;

    static public String extractEmail(String from) {
        var startBracket = from.lastIndexOf('<');
        // no brackets mean we just have an email address
        if (startBracket == -1) return from;
        var endBracket = from.lastIndexOf('>');
        if (endBracket < startBracket) throw new RuntimeException("badly formatted email: " + from);
        return from.substring(startBracket + 1, endBracket);
    }

    static public String normalizeEmail(String email) {
        var parts = email.split("@");
        var user = parts[0].toLowerCase();
        var host = parts[1].toLowerCase();
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        user = user.replace(".", "");
        return user + '@' + host;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        var cfg = MothConfiguration.mothConfiguration;
        domain = cfg.getServerName();
        if (cfg.getSMTPLocalPort() == -1 || cfg.getSMTPServerHost() == null) {
            log.warn("SMTP configuration not fully present. skipping email set up.");
            return;
        }
        mailer = MailerBuilder.withDebugLogging(true)
                .withSMTPServer(cfg.getSMTPServerHost(), cfg.getSMTPServerPort())
                .withTransportStrategy(TransportStrategy.SMTP)
                .withProperty("mail.smtp.localhost", domain)
                .buildMailer();
        var myMailObjectFactory = new BaseMailObjectFactory() {
            @Override
            public MailObject newMailObject(SmtpSession session) {
                return new MyBaseMailObject();
            }
        };
        var serverConfig = ServerConfig.builder()
                .withListenPort(cfg.getSMTPLocalPort())
                .withListenHost("::")
                .withCommandHandlers(
                        List.of(new Ehlo(domain), new Helo(domain), new Mail(myMailObjectFactory), new Rcpt(),
                                new Data(), new Quit(), new Rset(), new Noop()))
                .withExtensions(List.of(new EightBitMime(), new SmtpUtf8(), new Pipelining()))
                .withConnectHandler(new Connect(domain))
                .build();
        var server = new NettySmtpServer(serverConfig);
        server.start();
    }

    /**
     * this class gleans to, from, subject, and message id to reply with a registration password.
     * it's very conservative and does not look at the body at all.
     */
    private class MyBaseMailObject extends BaseMailObject {
        String messageId;
        String to;
        String from;
        String subject;
        boolean blankSeen;
        boolean inReplyToSeen;

        @Override
        public void writeLine(byte[] line, int offset, int length) {
            if (!blankSeen) {
                var parts = new String(line, offset, length).split(":", 2);
                switch (parts[0].toLowerCase()) {
                    case "from" -> from = parts[1].strip();
                    case "to" -> to = parts[1].strip();
                    case "subject" -> subject = parts[1].strip();
                    case "message-id" -> messageId = parts[1].strip();
                    case "in-reply-to" -> inReplyToSeen = true;
                    case "" -> blankSeen = true;
                }
            }
        }

        @Override
        public Reply complete(SmtpSession session) {
            // only reply to fresh messages that don't talk about deamons or failures
            log.info("received mail from %s to %s about %s".formatted(from, to, subject));
            if (!inReplyToSeen && !from.contains("mailer-daemon") && !subject.contains("ailure")) {
                var emailId = extractEmail(from);
                var eb = EmailBuilder.startingBlank()
                        .from(to)
                        .to(from)
                        .withHeader("In-Reply-To", messageId)
                        .withHeader("References", messageId);
                var registration = subject.contains("regist");
                var reset = subject.contains("reset");
                var replySubject = "re: " + subject;
                Mono<Email> mono;
                if (registration == reset) {
                    mono = Mono.just(eb.withSubject(replySubject)
                                             .withPlainText(getMessage("emailUnrecognizedReply"))
                                             .buildEmail());
                } else {
                    mono = emailRegistrationRepository.findById(normalizeEmail(emailId)).flatMap(reg -> {
                        if (registration) {
                            eb.withSubject(replySubject).withPlainText(getMessage("emailAlreadyRegistered"));
                            return Mono.just(eb.buildEmail());
                        } else {
                            var password = Util.generatePassword();
                            reg.saltedPassword = Util.encodePassword(password);
                            reg.lastEmail = Util.now();
                            return emailRegistrationRepository.save(reg)
                                    .thenReturn(eb.withSubject(getMessage("emailSubjectPasswordReset"))
                                                        .withPlainText(getMessage("emailNewPassword", password))
                                                        .buildEmail());
                        }
                    }).switchIfEmpty(Mono.defer(() -> {
                        if (registration) {
                            var password = Util.generatePassword();
                            var reg = new EmailRegistration();
                            reg.id = normalizeEmail(emailId);
                            reg.email = emailId;
                            reg.lastEmail = reg.firstEmail = Util.now();
                            reg.saltedPassword = Util.encodePassword(password);
                            return emailRegistrationRepository.save(reg)
                                    .thenReturn(eb.withSubject(getMessage("emailSubjectWelcome", domain))
                                                        .withPlainText(getMessage("emailNewPassword", password))
                                                        .buildEmail());
                        } else {
                            eb.withSubject(replySubject).withPlainText(getMessage("emailNotRegistered"));
                            return Mono.just(eb.buildEmail());

                        }
                    }));
                }
                mono.doOnNext(email -> log.info("sending " + email.getSubject() + " to " + email.getToRecipients()))
                        .map(mailer::sendMail)
                        .block();
            }
            return super.complete(session);
        }

        @NotNull
        private String getMessage(String code, String... args) {
            return messageSource.getMessage(code, args, Locale.getDefault());
        }
    }
}