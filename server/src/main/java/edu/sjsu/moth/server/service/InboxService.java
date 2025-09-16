package edu.sjsu.moth.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import edu.sjsu.moth.generated.Actor;
import edu.sjsu.moth.generated.Attachment;
import edu.sjsu.moth.generated.CustomEmoji;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.AccountField;
import edu.sjsu.moth.server.db.ExternalStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class InboxService {

    @Autowired
    ActorService actorService;

    @Autowired
    StatusService statusService;

    private static final String PUBLIC_URI = "https://www.w3.org/ns/activitystreams#Public";

    public Mono<ResponseEntity<Object>> createHandler(@RequestBody JsonNode node) {
        JsonNode objNode = node.get("object");
        String toLink = objNode.get("id").asText();

        //putting in variables for now to make it easier to read
        String id = toLink.substring(toLink.indexOf("/statuses/") + "/statuses/".length());
        String createdAt = node.get("published").asText();
        String inReplyTo = objNode.get("inReplyTo").asText();
        Boolean sensitive = objNode.get("sensitive").asText().equals("true");
        String language = objNode.get("contentMap").fields().next().getKey();
        String content = objNode.get("content").asText();
        String visibility = getVisibility(node);

        //Making an actor and then converting to account
        String accountLink = node.get("actor").asText();
        return actorService.getActor(accountLink).switchIfEmpty(createActor(accountLink))
                .flatMap(InboxService::convertToAccount).flatMap(account -> {
                    //not sure about spoiler text
                    //haven't implemented media service yet, not sure about visibility
                    //changed inreplyto to null
                    ExternalStatus status =
                            new ExternalStatus(null, createdAt, null, null, sensitive, "", visibility, language, null,
                                               null, 0, 0, 0, false, false, false, false, content, null, null, account,
                                               List.of(), List.of(), List.of(), List.of(), null, null, content,
                                               node.get("published").asText());
                    return statusService.saveExternal(status).map(ResponseEntity::ok);
                });
    }

    public static Mono<Account> convertToAccount(Actor actor) {
        String serverName = "";
        try {
            serverName = new URL(actor.id).getHost();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        ArrayList<AccountField> accountFields = new ArrayList<>();
        for (Attachment attachment : actor.attachment) {
            AccountField accountField = new AccountField(attachment.name, attachment.value, null);
            accountFields.add(accountField);
        }

        String iconLink;
        if (actor.icon != null) {
            iconLink = actor.icon.url;
        } else {
            iconLink = "";
        }

        String imageLink;
        if (actor.image != null) {
            imageLink = actor.image.url;
        } else {
            imageLink = "";
        }

        WebClient webClient =
                WebClient.builder().defaultHeader(HttpHeaders.ACCEPT, "application/activity+json").build();
        Mono<JsonNode> outboxResponse = webClient.get().uri(actor.outbox).retrieve().bodyToMono(JsonNode.class);
        Mono<JsonNode> followersResponse = webClient.get().uri(actor.outbox).retrieve().bodyToMono(JsonNode.class);
        Mono<JsonNode> followingResponse = webClient.get().uri(actor.outbox).retrieve().bodyToMono(JsonNode.class);
        String finalServerName = serverName;
        return outboxResponse.flatMap(jsonNodeOutbox -> {
            int totalItems = jsonNodeOutbox.get("totalItems").asInt();
            return followersResponse.flatMap(jsonNodeFollowers -> {
                int totalItemFollowers = jsonNodeFollowers.get("totalItems").asInt();
                return followingResponse.map(jsonNodeFollowing -> {
                    int totalItemFollowing = jsonNodeFollowing.get("totalItems").asInt();
                    //change avatar, avatar static, header, header static, last status to "" from iconLink and imageLink
                    //change from String.valueOf(generateUniqueId()) to just their name
                    //changed last status from null to actor.published
                    return new Account(actor.preferredUsername, actor.preferredUsername + "@" + finalServerName,
                                       actor.url, actor.name, actor.summary, iconLink, iconLink, imageLink, imageLink,
                                       actor.manuallyApprovesFollowers, accountFields, new CustomEmoji[0], false, false,
                                       actor.discoverable, false, false, false, false, actor.published, actor.published,
                                       totalItems, totalItemFollowers, totalItemFollowing);
                });
            });
        });
        //don't know about custom emojis, bot, and group
        //noindex, moved, suspended, and limited are optional?
        //icon, image, tag, attachment might be null
        //not sure how to get last_status_at. outbox doesn't give a time, only the last status
    }

    public Mono<Actor> createActor(String accountLink) {
        WebClient webClient =
                WebClient.builder().defaultHeader(HttpHeaders.ACCEPT, "application/activity+json").build();
        Mono<Actor> response = webClient.get().uri(accountLink).retrieve().bodyToMono(Actor.class);
        return response.flatMap(actor -> actorService.save(actor));
    }

    public static String getVisibility(JsonNode activity) {
        // Extract recipients from outer Create activity
        Set<String> toRecipients = extractRecipients(activity, "to");
        Set<String> ccRecipients = extractRecipients(activity, "cc");

        // Extract recipients from inner Note object (if present)
        JsonNode object = activity.get("object");
        if (object != null) {
            toRecipients.addAll(extractRecipients(object, "to"));
            ccRecipients.addAll(extractRecipients(object, "cc"));
        }

        // Logic to determine visibility
        boolean toPublic = toRecipients.contains(PUBLIC_URI);
        boolean ccPublic = ccRecipients.contains(PUBLIC_URI);

        int totalRecipients = toRecipients.size() + ccRecipients.size();

        if (toPublic && ccRecipients.stream().anyMatch(s -> s.endsWith("/followers"))) {
            return "public";
        } else if (!toPublic && !ccPublic && toRecipients.stream().anyMatch(s -> s.endsWith("/followers"))) {
            return "private";
        } else if (!toPublic && !ccPublic && toRecipients.stream().noneMatch(s -> s.endsWith("/followers"))) {
            return "direct";
        }
        return "unlisted";
    }

    private static Set<String> extractRecipients(JsonNode obj, String key) {
        Set<String> recipients = new HashSet<>();

        if (obj.has(key)) {
            JsonNode node = obj.get(key);

            if (node.isArray()) {
                for (JsonNode element : node) {
                    if (element.isTextual()) {
                        recipients.add(element.asText());
                    }
                }
            } else if (node.isTextual()) {
                recipients.add(node.asText());
            }
        }

        return recipients;
    }
}
