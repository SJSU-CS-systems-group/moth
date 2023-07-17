package edu.sjsu.moth.service;

import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.service.AccountService;
import edu.sjsu.moth.server.service.ContactAccountChecker;
import edu.sjsu.moth.server.util.MothConfiguration;
import edu.sjsu.moth.util.MockitoFactoryBean;
import edu.sjsu.moth.util.MothTestInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;

@ExtendWith(SpringExtension.class)
@Configuration
public class ContactAccountCheckerTest {
    @Bean
    public FactoryBean<AccountService> accountService() {
        return new MockitoFactoryBean<>(AccountService.class);
    }

    @Test
    public void testContactAccountCreate() throws Exception {
        var ctx = new AnnotationConfigApplicationContext(ContactAccountCheckerTest.class);
        new MothTestInitializer().initialize(ctx);
        var contact = MothConfiguration.mothConfiguration.getAccountName();

        // configure the ContactAccountChecker. it need a context with an AccountService bean
        ctx.register(ContactAccountCheckerTest.class);
        ctx.register(ContactAccountChecker.class);
        var accountService = ctx.getBean("accountService", AccountService.class);

        // mock that contact account doesn't exist
        Mockito.when(accountService.getAccount(Mockito.any())).thenReturn(Mono.empty());
        Mockito.when(accountService.createAccount(Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(i -> Mono.just(new Account(i.getArgument(0))));

        var contactAccountChecker = ctx.getBean(ContactAccountChecker.class);
        contactAccountChecker.run(null);
        Mockito.verify(accountService, Mockito.times(1)).createAccount(Mockito.anyString(), Mockito.anyString());

        // mock that contact account exists
        Mockito.when(accountService.getAccount(contact)).thenReturn(Mono.just(new Account(contact)));
        contactAccountChecker.run(null);
        Mockito.verify(accountService, Mockito.times(1)).createAccount(Mockito.anyString(), Mockito.anyString());
    }
}