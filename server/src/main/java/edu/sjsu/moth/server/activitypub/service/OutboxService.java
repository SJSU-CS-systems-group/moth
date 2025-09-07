package edu.sjsu.moth.server.activitypub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.sjsu.moth.generated.Status;
import edu.sjsu.moth.server.activitypub.ActivityPubUtil;
import edu.sjsu.moth.server.activitypub.message.CreateMessage;
import edu.sjsu.moth.server.activitypub.message.NoteMessage;
import edu.sjsu.moth.server.db.AccountRepository;
import edu.sjsu.moth.server.db.OutboxRepository;
import edu.sjsu.moth.server.service.VisibilityService;
import edu.sjsu.moth.server.service.VisibilityService.VISIBILITY;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springframework.beans.support.PagedListHolder.DEFAULT_PAGE_SIZE;

@CommonsLog
@Configuration
public class OutboxService {

    @Autowired
    AccountRepository accountRepository;

    private final ObjectMapper objectMapper;

    @Autowired
    private OutboxRepository outboxRepository;

    public OutboxService() {
        this.objectMapper = new ObjectMapper();
    }

    public CreateMessage buildCreateActivity(Status status) {
        String actorUrl = ActivityPubUtil.toActivityPubUserUrl(status.account.url);
        NoteMessage note = buildNoteMessage(status, actorUrl);

        return new CreateMessage(actorUrl, note);
    }

    private NoteMessage buildNoteMessage(Status status, String actorUrl) {
        NoteMessage.Replies.First first = new NoteMessage.Replies.First();
        first.setNext(status.getUri() + "/replies?only_other_accounts=true&page=true");
        first.setPartOf(status.getUri() + "/replies");
        first.setItems(Collections.emptyList());
        String to = "";
        String cc = "";

        VISIBILITY visibility = VisibilityService.visibilityFromString(Optional.ofNullable(status.visibility));
        if (visibility == VISIBILITY.PRIVATE) {
            to = actorUrl + "/followers";
            cc = "";
        } else {
            to = "https://www.w3.org/ns/activitystreams#Public";
            cc = actorUrl + "/followers";
        }

        NoteMessage.Replies replies = new NoteMessage.Replies();
        replies.setId(status.getUri() + "/replies");
        replies.setFirst(first);

        return new NoteMessage(status.getUri(), null, null, status.createdAt, status.getUrl(), actorUrl, List.of(to),
                               List.of(cc), status.sensitive, status.getUri(), null, status.text, status.content,
                               Map.of("en", status.content), Collections.emptyList(), Collections.emptyList(), replies);
    }

    public Mono<ResponseEntity<JsonNode>> getOutboxIndex(String username, String baseOutbox) {

        return accountRepository.findItemByAcct(username).flatMap(account -> {
            // build the JSON body
            ObjectNode body = buildOutboxCollection(account.statuses_count, baseOutbox);

            return Mono.just(ResponseEntity.ok().contentType(MediaType.parseMediaType("application/activity+json"))
                                     .header(HttpHeaders.CACHE_CONTROL, "public, max-age=180").body((JsonNode) body));
        }).switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build()));
    }

    private ObjectNode buildOutboxCollection(long totalItems, String baseOutbox) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("@context", "https://www.w3.org/ns/activitystreams");
        root.put("id", baseOutbox);
        root.put("type", "OrderedCollection");
        root.put("totalItems", totalItems);
        root.put("first", baseOutbox + "?page=true");
        root.put("last", baseOutbox + "?page=true&min_id=0");
        return root;
    }

    public Mono<ResponseEntity<JsonNode>> getOutbox(String username, String actorUrl, String minId, Integer limit) {
        final int pageSize = limit != null && limit > 0 ? limit : DEFAULT_PAGE_SIZE;
        final String baseOutbox = actorUrl + "/outbox";
        return outboxRepository.findAllByActorOrderByPublishedAtDesc(actorUrl).collectList().flatMap(messages -> {
            // cursor‐style pagination
            List<CreateMessage> pageItems = new ArrayList<>();
            boolean skipping = (minId != null);
            for (CreateMessage msg : messages) {
                if (skipping) {
                    if (msg.getId().equals(minId)) skipping = false;
                    continue;
                }
                pageItems.add(msg);
                if (pageItems.size() >= pageSize) break;
            }

            // build JSON body
            ObjectNode root = objectMapper.createObjectNode();

            // @context array
            ArrayNode ctx = root.putArray("@context");
            ctx.add("https://www.w3.org/ns/activitystreams");
            ObjectNode extras = objectMapper.createObjectNode().put("ostatus", "http" + "://ostatus.org#")
                    .put("atomUri", "ostatus:atomUri").put("inReplyToAtomUri", "ostatus:inReplyToAtomUri")
                    .put("conversation", "ostatus:conversation").put("sensitive", "as" + ":sensitive")
                    .put("toot", "http://joinmastodon.org/ns#").put("votersCount", "toot" + ":votersCount");
            ctx.add(extras);

            // top‐level metadata
            String queryString = "?limit=" + pageSize +
                    (minId != null ? "&min_id=" + URLEncoder.encode(minId, StandardCharsets.UTF_8) + "&page" + "=true" :
                            "?page=true");
            root.put("id", baseOutbox + queryString);
            root.put("type", "OrderedCollectionPage");
            if (!pageItems.isEmpty()) {
                String statusUrl = pageItems.get(0).object.getId();
                String statusId = statusUrl.substring(statusUrl.lastIndexOf('/') + 1);
                String prevQs =
                        "?min_id=" + URLEncoder.encode(statusId, StandardCharsets.UTF_8) + "&limit=" + pageSize +
                                "&page=true";
                root.put("prev", baseOutbox + prevQs);
            }
            root.put("partOf", baseOutbox);

            // orderedItems
            ArrayNode items = root.putArray("orderedItems");
            for (CreateMessage msg : pageItems) {
                items.add(objectMapper.valueToTree(msg));
            }
            return Mono.just(ResponseEntity.ok().contentType(MediaType.parseMediaType("application/activity+json"))
                                     .body((JsonNode) root));
        }).switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build())).onErrorResume(ex -> {
            log.error("Error building outbox for %s :%s".formatted(username, ex));
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
        });
    }
}
