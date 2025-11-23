package edu.sjsu.moth.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.sjsu.moth.generated.Actor;
import edu.sjsu.moth.server.db.ExternalStatus;
import edu.sjsu.moth.server.util.MothConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class BackfillServiceTest {

    private final ObjectMapper om = new ObjectMapper();
    private ActorService actorService;
    private RemoteOutboxFetcher outboxFetcher;
    private RemoteStatusIngestService ingestService;
    private BackfillService backfillService;

    @BeforeEach
    public void setup() throws IOException {
        try {
            var resource = BackfillServiceTest.class.getResource("/test.cfg");
            if (resource == null) {
                throw new RuntimeException("test.cfg not found in classpath");
            }
            new MothConfiguration(new File(resource.getFile()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to init test config", e);
        }

        actorService = Mockito.mock(ActorService.class);
        // Ensure switchIfEmpty doesn't fail with NPE on null return from mock
        when(actorService.fetchAndSaveActorById(any())).thenReturn(Mono.empty());
        when(actorService.getActor(anyString())).thenReturn(Mono.empty());

        outboxFetcher = Mockito.mock(RemoteOutboxFetcher.class);
        ingestService = Mockito.mock(RemoteStatusIngestService.class);
        backfillService = new BackfillService(actorService, outboxFetcher, ingestService);
    }

    @Test
    public void resolveActor_fromAcct_formatsCanonicalActorId() {
        String acct = "alice@example.com";
        String expectedActorId = "https://example.com/users/alice";

        Actor actor = new Actor();
        actor.id = expectedActorId;
        actor.outbox = "https://example.com/users/alice/outbox";

        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        when(actorService.getActor(idCaptor.capture())).thenReturn(Mono.just(actor));
        when(outboxFetcher.fetchCreateActivities(eq(actor.outbox), any())).thenReturn(Flux.empty());
        when(ingestService.ingestCreateNotes(any(), eq(actor), any())).thenReturn(Mono.just(List.of()));

        Integer count = backfillService.runBackfillOnce(acct, BackfillService.BackfillType.SEARCH).block();
        assertNotNull(count);
        assertEquals(0, count.intValue());
        assertEquals(expectedActorId, idCaptor.getValue());
    }

    @Test
    public void searchLimit_appliesMaxStatusesToIngest() {
        String actorId = "https://remote.example/users/bob";
        Actor actor = new Actor();
        actor.id = actorId;
        actor.outbox = actorId + "/outbox";

        when(actorService.getActor(actorId)).thenReturn(Mono.just(actor));

        // Build 160 Create(Note) items (SEARCH default max = 150)
        List<JsonNode> items = new ArrayList<>();
        for (int i = 0; i < 160; i++) {
            items.add(buildCreateNote("https://remote.example/activities/" + i,
                                      Instant.now().minus(i, ChronoUnit.MINUTES)));
        }
        when(outboxFetcher.fetchCreateActivities(eq(actor.outbox), any())).thenReturn(Flux.fromIterable(items));

        // Capture the upstream size that ingest sees by materializing it
        when(ingestService.ingestCreateNotes(any(), eq(actor), any())).then(inv -> {
            Flux<JsonNode> flux = inv.getArgument(0);
            List<JsonNode> received = flux.collectList().block();
            List<ExternalStatus> mapped = new ArrayList<>();
            for (int i = 0; i < received.size(); i++) mapped.add(Mockito.mock(ExternalStatus.class));
            return Mono.just(mapped);
        });

        Integer count = backfillService.runBackfillOnce(actorId, BackfillService.BackfillType.SEARCH).block();
        assertNotNull(count);
        assertEquals(150, count.intValue(), "SEARCH backfill should cap at 150 items");
    }

    @Test
    public void followMaxAge_excludesOlderThan30Days_andStopsAtBoundary() {
        String actorId = "https://remote.example/users/cara";
        Actor actor = new Actor();
        actor.id = actorId;
        actor.outbox = actorId + "/outbox";

        when(actorService.getActor(actorId)).thenReturn(Mono.just(actor));

        // Order: recent, recent, old(31d). takeWhile should include 2, then stop.
        JsonNode recent1 = buildCreateNote("https://remote.example/a/1", Instant.now().minus(1, ChronoUnit.DAYS));
        JsonNode recent2 = buildCreateNote("https://remote.example/a/2", Instant.now().minus(10, ChronoUnit.DAYS));
        JsonNode old = buildCreateNote("https://remote.example/a/3", Instant.now().minus(31, ChronoUnit.DAYS));
        when(outboxFetcher.fetchCreateActivities(eq(actor.outbox), any())).thenReturn(Flux.just(recent1, recent2, old));

        when(ingestService.ingestCreateNotes(any(), eq(actor), any())).then(inv -> {
            Flux<JsonNode> flux = inv.getArgument(0);
            List<JsonNode> received = flux.collectList().block();
            List<ExternalStatus> mapped = new ArrayList<>();
            for (int i = 0; i < received.size(); i++) mapped.add(Mockito.mock(ExternalStatus.class));
            return Mono.just(mapped);
        });

        Integer count = backfillService.runBackfillOnce(actorId, BackfillService.BackfillType.FOLLOW).block();
        assertNotNull(count);
        assertEquals(2, count.intValue(), "FOLLOW backfill should exclude items older than 30 days");
    }

    private JsonNode buildCreateNote(String id, Instant published) {
        ObjectNode note = om.createObjectNode();
        note.put("type", "Note");
        note.put("id", id);
        note.put("content", "<p>Test</p>");

        ObjectNode create = om.createObjectNode();
        create.put("type", "Create");
        create.put("published", published.truncatedTo(ChronoUnit.SECONDS).toString());
        create.set("object", note);
        return create;
    }
}
