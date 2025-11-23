package edu.sjsu.moth.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.db.FollowRepository;
import edu.sjsu.moth.server.db.PubKeyPairRepository;
import edu.sjsu.moth.server.db.WebfingerRepository;
import edu.sjsu.moth.server.util.MothConfiguration;
import edu.sjsu.moth.server.worker.ContactAccountChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@SpringBootTest
@AutoConfigureDataMongo
@Import({ AccountService.class })
public class AccountServiceBackfillTriggerTest {

    static {
        URL temp = AccountServiceBackfillTriggerTest.class.getResource("/test.cfg");
        try {
            new MothConfiguration(new File(temp.getFile()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Autowired
    AccountService accountService;
    @MockBean
    ActivityPubService activityPubService;
    @MockBean
    EmailService emailService;
    @MockBean
    FollowService followService;
    @MockBean
    WebfingerRepository webfingerRepository;
    @MockBean
    PubKeyPairRepository pubKeyPairRepository;
    @MockBean
    AccountRepository accountRepository;
    @MockBean
    FollowRepository followRepository;
    @MockBean
    BackfillService backfillService;
    @MockBean
    ObjectMapper objectMapper;
    @MockBean
    ContactAccountChecker contactAccountChecker;

    @MockBean
    edu.sjsu.moth.server.worker.EmailServiceChecker emailServiceChecker;

    @BeforeEach
    public void setup() {
        Mockito.reset(backfillService, activityPubService, accountRepository, followService);
    }

    @Test
    public void triggersBackfill_onAcceptForRemoteFollowedAccount() {
        String targetId = "localUser";
        Account target = new Account();
        target.id = targetId;

        Account followedRemote = new Account();
        followedRemote.id = "Gargron@mastodon.social";
        followedRemote.acct = "Gargron@mastodon.social";

        Mockito.when(accountRepository.findById(eq(targetId))).thenReturn(Mono.just(target));
        Mockito.when(accountRepository.findItemByAcct(eq("Gargron@mastodon.social")))
                .thenReturn(Mono.just(followedRemote));
        Mockito.when(followService.saveOutgoingRemoteFollow(any(), any())).thenReturn(Mono.just("ok"));

        ObjectNode acceptBody = new ObjectMapper().createObjectNode();
        acceptBody.put("actor", "https://mastodon.social/users/Gargron");

        accountService.acceptHandler(targetId, acceptBody).block();

        Mockito.verify(backfillService)
                .backfillRemoteAcctAsync(eq("Gargron@mastodon.social"), eq(BackfillService.BackfillType.FOLLOW));
    }

    @Test
    public void doesNotTriggerBackfill_onAcceptForLocalFollowedAccount() {
        String targetId = "localUser";
        Account target = new Account();
        target.id = targetId;

        Account followedLocal = new Account();
        followedLocal.id = "bob";
        followedLocal.acct = "bob";

        Mockito.when(accountRepository.findById(eq(targetId))).thenReturn(Mono.just(target));
        Mockito.when(accountRepository.findItemByAcct(eq("bob"))).thenReturn(Mono.just(followedLocal));
        Mockito.when(followService.saveOutgoingRemoteFollow(any(), any())).thenReturn(Mono.just("ok"));

        ObjectNode acceptBody = new ObjectMapper().createObjectNode();
        acceptBody.put("actor", "https://localhost/users/bob");

        accountService.acceptHandler(targetId, acceptBody).block();

        Mockito.verifyNoInteractions(backfillService);
    }
}