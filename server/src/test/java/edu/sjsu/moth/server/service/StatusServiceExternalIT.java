package edu.sjsu.moth.server.service;

import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.embed.process.io.ProcessOutput;
import de.flapdoodle.reverse.TransitionWalker;
import de.flapdoodle.reverse.transitions.Start;
import edu.sjsu.moth.server.MothServerMain;
import edu.sjsu.moth.server.db.ExternalStatusRepository;
import edu.sjsu.moth.server.util.MothConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;

import java.io.File;
import java.security.Principal;
import java.time.Duration;

@EnabledIfSystemProperty(named = "externalTests", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureDataMongo
@AutoConfigureWebTestClient
@ComponentScan(basePackageClasses = MothServerMain.class)
public class StatusServiceExternalIT {

    static private final int RAND_MONGO_PORT = 27017 + new java.util.Random().nextInt(17, 37);
    static private TransitionWalker.ReachedState<RunningMongodProcess> eMongod;

    static {
        try {
            var fullname = edu.sjsu.moth.IntegrationTest.class.getResource("/test.cfg").getFile();
            System.out.println(new MothConfiguration(new File(fullname)).properties);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(2);
        }
    }

    @Autowired
    ActorService actorService;
    @Autowired
    StatusService statusService;
    @Autowired
    ExternalStatusRepository externalStatusRepository;

    @BeforeAll
    static void setup() {
        String useLocalMongo = System.getProperty("useLocalMongo", "false");
        if ("true".equalsIgnoreCase(useLocalMongo)) {
            System.setProperty("spring.data.mongodb.port", "27017");
            String dbName = System.getProperty("mongoDbName", "moth_test");
            System.setProperty("spring.data.mongodb.database", dbName);
            System.out.println("Using local MongoDB at 27017, database=" + dbName);
            return;
        }
        eMongod = Mongod.builder().processOutput(Start.to(ProcessOutput.class).initializedWith(ProcessOutput.silent()))
                .net(Start.to(Net.class).initializedWith(Net.defaults().withPort(RAND_MONGO_PORT))).build()
                .start(Version.Main.V6_0);
        System.setProperty("spring.data.mongodb.port", Integer.toString(RAND_MONGO_PORT));
    }

    @AfterAll
    static void clean() {
        if (eMongod != null) eMongod.close();
    }

    @Test
    public void fetchGargronOutboxAndIngest() {
        // 1) Resolve and cache the actor
        var acct = "Alice@mastodon.social";
        actorService.resolveRemoteAccount(acct).block(Duration.ofSeconds(30));

        // 2) Trigger remote ingest via getStatusesForId
        Principal p = () -> "test-user";
        var list = statusService.getStatusesForId(p, acct, null, null, null, false, false, false, null, null, 5)
                .block(Duration.ofSeconds(60));

        Assertions.assertNotNull(list);

        // 3) Verify something exists for this actor in ExternalStatusRepository
        var one = externalStatusRepository.findAllByAccount_AcctOrderByCreatedAtDesc(acct).take(1).collectList()
                .block(Duration.ofSeconds(30));
        Assertions.assertNotNull(one);
        Assertions.assertFalse(one.isEmpty(), "Expected at least one ingested status for " + acct);
    }
}
