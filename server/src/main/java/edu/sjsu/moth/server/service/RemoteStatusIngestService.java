package edu.sjsu.moth.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import edu.sjsu.moth.generated.Actor;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.ExternalStatus;
import edu.sjsu.moth.server.db.ExternalStatusRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;

@Service
public class RemoteStatusIngestService {

    private final ExternalStatusRepository externalStatusRepository;

    public RemoteStatusIngestService(ExternalStatusRepository repo) {
        this.externalStatusRepository = repo;
    }

    public Mono<List<ExternalStatus>> ingestCreateNotes(Flux<JsonNode> createActivities, Actor actor,
                                                        Function<Actor, Mono<Account>> accountProvider) {
        return accountProvider.apply(actor).flatMap(account -> createActivities.flatMap(item -> {
            JsonNode obj = item.path("object");
            String id = text(obj.path("id"));
            if (id == null || id.isBlank()) return Mono.empty();

            return externalStatusRepository.findById(id).switchIfEmpty(mapNoteToExternal(obj, item, account))
                    .flatMap(existingOrNew -> Mono.just(existingOrNew));
        }).collectList());
    }

    private Mono<ExternalStatus> mapNoteToExternal(JsonNode note, JsonNode create, Account acc) {
        String id = text(note.path("id"));
        String published = text(create.path("published"));
        boolean sensitive = note.path("sensitive").asBoolean(false);
        String content = text(note.path("content"));
        String language = null;
        JsonNode contentMap = note.path("contentMap");
        if (contentMap != null && contentMap.fieldNames().hasNext()) {
            language = contentMap.fieldNames().next();
        }

        ExternalStatus status =
                new ExternalStatus(id, published, null, null, sensitive, "", "public", language, id, id, 0, 0, 0, false,
                                   false, false, false, content, null, null, acc, List.of(), List.of(), List.of(),
                                   List.of(), null, null, content, published);
        return externalStatusRepository.save(status);
    }

    private String text(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }
}
