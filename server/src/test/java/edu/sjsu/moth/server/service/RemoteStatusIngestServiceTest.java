package edu.sjsu.moth.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.sjsu.moth.generated.Actor;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.ExternalStatus;
import edu.sjsu.moth.server.db.ExternalStatusRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class RemoteStatusIngestServiceTest {

    @Test
    public void testIngestSingleCreateNote() throws Exception {
        ExternalStatusRepository repo = Mockito.mock(ExternalStatusRepository.class);
        RemoteStatusIngestService svc = new RemoteStatusIngestService(repo);

        ObjectMapper om = new ObjectMapper();
        // Build a minimal Create(Note) activity
        ObjectNode note = om.createObjectNode();
        note.put("type", "Note");
        note.put("id", "https://remote.example/users/alice/statuses/1");
        note.put("content", "<p>Hello</p>");

        ObjectNode create = om.createObjectNode();
        create.put("type", "Create");
        create.put("published", "2025-01-01T00:00:00Z");
        create.set("object", note);

        Flux<JsonNode> activities = Flux.just((JsonNode) create);

        Actor actor = new Actor();
        actor.id = "https://remote.example/users/alice";
        actor.preferredUsername = "alice";
        actor.url = "https://remote.example/@alice";

        Function<Actor, Mono<Account>> provider = a -> {
            Account acc = new Account();
            acc.id = "alice@remote.example";
            acc.username = "alice";
            acc.acct = "alice@remote.example";
            acc.url = "https://remote.example/@alice";
            return Mono.just(acc);
        };

        when(repo.findById(Mockito.anyString())).thenReturn(Mono.empty());
        when(repo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        List<ExternalStatus> saved = svc.ingestCreateNotes(activities, actor, provider).block();
        assertNotNull(saved);
        assertEquals(1, saved.size());
        assertEquals("https://remote.example/users/alice/statuses/1", saved.get(0).id);
        assertEquals("<p>Hello</p>", saved.get(0).content);
    }

    @Test
    public void testIngestSanitizesHostileHtml() throws Exception {
        ExternalStatusRepository repo = Mockito.mock(ExternalStatusRepository.class);
        RemoteStatusIngestService svc = new RemoteStatusIngestService(repo);

        ObjectMapper om = new ObjectMapper();
        ObjectNode note = om.createObjectNode();
        note.put("type", "Note");
        note.put("id", "https://remote.example/users/mallory/statuses/666");
        note.put("content", "<p>hi</p><script>alert(1)</script><img src=x onerror=pwn()>" +
                "<a href=\"javascript:steal()\">click</a>");

        ObjectNode create = om.createObjectNode();
        create.put("type", "Create");
        create.put("published", "2025-01-01T00:00:00Z");
        create.set("object", note);

        Actor actor = new Actor();
        actor.id = "https://remote.example/users/mallory";
        actor.preferredUsername = "mallory";
        actor.url = "https://remote.example/@mallory";

        Function<Actor, Mono<Account>> provider = a -> {
            Account acc = new Account();
            acc.id = "mallory@remote.example";
            return Mono.just(acc);
        };

        when(repo.findById(Mockito.anyString())).thenReturn(Mono.empty());
        when(repo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        List<ExternalStatus> saved = svc.ingestCreateNotes(Flux.just((JsonNode) create), actor, provider).block();
        assertNotNull(saved);
        assertEquals(1, saved.size());
        var content = saved.get(0).content;
        assertNotNull(content);
        assertTrue(content.contains("<p>hi</p>"), "benign markup should survive: " + content);
        assertFalse(content.contains("<script"), "script tags must be stripped: " + content);
        assertFalse(content.contains("onerror"), "event handlers must be stripped: " + content);
        assertFalse(content.contains("javascript:"), "javascript: URLs must be stripped: " + content);
    }
}
