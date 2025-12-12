package edu.sjsu.moth.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import edu.sjsu.moth.generated.Actor;
import edu.sjsu.moth.server.db.ExternalStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class InboxService {

    private static final String PUBLIC_URI = "https://www.w3.org/ns/activitystreams#Public";
    @Autowired
    StatusService statusService;
    @Autowired
    AccountService accountService;
    @Autowired
    ActorService actorService;

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

    // mapping media to img
    private static String mapMediaTypeToMastoType(String mediaType, String asType) {
        if (mediaType == null) mediaType = "";
        String lower = mediaType.toLowerCase();
        if (lower.startsWith("image/")) return "image";
        if (lower.equals("image/gif")) return "gifv";
        if (lower.startsWith("video/")) return "video";
        if (lower.startsWith("audio/")) return "audio";
        if ("Image".equalsIgnoreCase(asType)) return "image";
        if ("Video".equalsIgnoreCase(asType)) return "video";
        if ("Audio".equalsIgnoreCase(asType)) return "audio";
        return "image";
    }

    // get the media url
    private static String coalesceUrlFromAttachment(JsonNode a) {
        if (a.has("url") && a.get("url").isTextual()) return a.get("url").asText();
        if (a.has("href") && a.get("href").isTextual()) return a.get("href").asText();
        if (a.has("url") && a.get("url").isArray()) {
            for (JsonNode u : a.get("url")) {
                if (u.has("href") && u.get("href").isTextual()) return u.get("href").asText();
                if (u.isTextual()) return u.asText();
            }
        }
        return null;
    }

    // preview url or thumbnail url
    private static String coalescePreviewFromAttachment(JsonNode a) {
        if (a.has("icon") && a.get("icon").has("url") && a.get("icon").get("url").isTextual()) {
            return a.get("icon").get("url").asText();
        }
        if (a.has("url") && a.get("url").isArray()) {
            for (JsonNode u : a.get("url")) {
                String mediaType =
                        u.has("mediaType") && u.get("mediaType").isTextual() ? u.get("mediaType").asText() : "";
                String href = u.has("href") && u.get("href").isTextual() ? u.get("href").asText() : null;
                if (mediaType.startsWith("image/") && href != null) return href;
            }
        }
        return null;
    }

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
                .flatMap(actor -> accountService.convertToAccount(actor)).flatMap(account -> {
                    // build media attachments
                    var media = new java.util.ArrayList<edu.sjsu.moth.generated.MediaAttachment>();
                    var attachments = objNode.get("attachment");
                    if (attachments != null && attachments.isArray()) {
                        int idx = 0;
                        for (JsonNode a : attachments) {
                            String attachId = a.has("id") && a.get("id").isTextual() ? a.get("id").asText() :
                                    (id + ":att:" + idx);
                            String mediaType =
                                    a.has("mediaType") && a.get("mediaType").isTextual() ? a.get("mediaType").asText() :
                                            "";
                            String asType = a.has("type") && a.get("type").isTextual() ? a.get("type").asText() : null;
                            String type = mapMediaTypeToMastoType(mediaType, asType);

                            String url = coalesceUrlFromAttachment(a);
                            String previewUrl = coalescePreviewFromAttachment(a);
                            if (previewUrl == null) previewUrl = url;

                            String description = a.has("name") && a.get("name").isTextual() ? a.get("name").asText() :
                                    (a.has("summary") && a.get("summary").isTextual() ? a.get("summary").asText() :
                                            null);

                            java.util.Map<String, Object> meta = java.util.Map.of();
                            if (a.has("width") && a.has("height") && a.get("width").canConvertToInt() &&
                                    a.get("height").canConvertToInt()) {
                                int width = a.get("width").asInt();
                                int height = a.get("height").asInt();
                                double aspect = height == 0 ? 0.0 : (double) width / (double) height;
                                java.util.Map<String, Object> original = new java.util.HashMap<>();
                                original.put("width", width);
                                original.put("height", height);
                                original.put("size", width + "x" + height);
                                original.put("aspect", aspect);
                                java.util.Map<String, Object> m = new java.util.HashMap<>();
                                m.put("original", original);
                                meta = m;
                            }

                            String blurhash =
                                    a.has("blurhash") && a.get("blurhash").isTextual() ? a.get("blurhash").asText() :
                                            null;

                            media.add(new edu.sjsu.moth.generated.MediaAttachment(attachId, type, url, previewUrl, url,
                                                                                  null, meta, description, blurhash));
                            idx++;
                        }
                    }

                    // saving it to ExternalStatuses db
                    ExternalStatus status =
                            new ExternalStatus(null, createdAt, null, null, sensitive, "", "direct", language, null,
                                               null, 0, 0, 0, false, false, false, false, content, null, null, account,
                                               media, List.of(), List.of(), List.of(), null, null, content,
                                               node.get("published").asText());
                    return statusService.saveExternal(status).map(ResponseEntity::ok);
                });
    }

    public Mono<Actor> createActor(String accountLink) {
        WebClient webClient =
                WebClient.builder().defaultHeader(HttpHeaders.ACCEPT, "application/activity+json").build();
        Mono<Actor> response = webClient.get().uri(accountLink).retrieve().bodyToMono(Actor.class);
        return response.flatMap(actor -> actorService.save(actor));
    }
}
