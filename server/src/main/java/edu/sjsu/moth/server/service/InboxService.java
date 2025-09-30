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
    StatusService statusService;

    @Autowired
    AccountService accountService;

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

        return accountService.fetchAccount(accountLink).flatMap(account -> {
            //not sure about spoiler text
            //haven't implemented media service yet, not sure about visibility
            //changed inreplyto to null
            ExternalStatus status =
                    new ExternalStatus(null, createdAt, null, null, sensitive, "", visibility, language, null, null, 0,
                                       0, 0, false, false, false, false, content, null, null, account, List.of(),
                                       List.of(), List.of(), List.of(), null, null, content,
                                       node.get("published").asText());
            return statusService.saveExternal(status).map(ResponseEntity::ok);
        });
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
