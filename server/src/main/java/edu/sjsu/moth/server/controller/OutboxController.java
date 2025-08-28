package edu.sjsu.moth.server.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sjsu.moth.server.activitypub.service.OutboxService;
import edu.sjsu.moth.server.util.MothConfiguration;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static org.springframework.beans.support.PagedListHolder.DEFAULT_PAGE_SIZE;

@CommonsLog
@RestController
public class OutboxController {

    ObjectMapper mappedLoad;

    @Autowired
    OutboxService outboxService;

    private final String serverName;

    public OutboxController(ObjectMapper mappedLoad) {
        this.mappedLoad = mappedLoad;
        this.serverName = MothConfiguration.mothConfiguration.getServerName();
    }

    /**
     * JSON:
     * {"@context":"https://www.w3.org/ns/activitystreams",
     * "id":"https://shirley.homeofcode.com/users/shirley/outbox",
     * "type":"OrderedCollection",
     * "totalItems":3,
     * "first":"https://shirley.homeofcode.com/users/shirley/outbox?page=true",
     * "last":"https://shirley.homeofcode.com/users/shirley/outbox?min_id=0\u0026page=true"}
     */
    @GetMapping("/users/{id}/outbox")
    public Mono<ResponseEntity<JsonNode>> fetchOutboxLink(
            @PathVariable String id,
            @RequestParam(required = false) Integer limit,
            @RequestParam(value = "min_id", required = false) String minId,
            @RequestParam(value = "page", defaultValue = "false") boolean page) {
        String actorUrl = "https://" + serverName + "/users/" + id;
        String baseOutbox = actorUrl + "/outbox";
        if (page) {
            return outboxService.getOutbox(id, actorUrl, minId, limit);
        } else {
            return outboxService.getOutboxIndex(id, baseOutbox);
        }

    }
}
