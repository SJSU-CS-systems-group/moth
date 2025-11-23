package edu.sjsu.moth.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import edu.sjsu.moth.generated.Actor;
import edu.sjsu.moth.generated.MediaAttachment;
import edu.sjsu.moth.server.db.Account;
import edu.sjsu.moth.server.db.ExternalStatus;
import edu.sjsu.moth.server.db.ExternalStatusRepository;
import edu.sjsu.moth.server.db.StatusMention;
import edu.sjsu.moth.server.db.StatusTag;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RemoteStatusIngestService {

    private final ExternalStatusRepository externalStatusRepository;

    public RemoteStatusIngestService(ExternalStatusRepository repo) {
        this.externalStatusRepository = repo;
    }

    public Mono<List<ExternalStatus>> ingestCreateNotes(Flux<JsonNode> createActivities, Actor actor,
                                                        AccountSnapshotProvider accountSnapshotProvider) {
        return accountSnapshotProvider.getAccountSnapshot(actor).flatMap(account -> createActivities.flatMap(item -> {
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
        var media = buildMediaAttachments(note.path("attachment"), id);
        var mentions = new ArrayList<StatusMention>();
        var tags = new ArrayList<StatusTag>();
        extractTags(note.path("tag"), mentions, tags);

        ExternalStatus status =
                new ExternalStatus(id, published, null, null, sensitive, "", "public", language, id, id, 0, 0, 0, false,
                                   false, false, false, content, null, null, acc, media, mentions, tags, List.of(),
                                   null, null, content, published);
        return externalStatusRepository.save(status);
    }

    private ArrayList<MediaAttachment> buildMediaAttachments(JsonNode attachments, String fallbackId) {
        var list = new ArrayList<MediaAttachment>();
        if (attachments == null || !attachments.isArray()) return list;
        int idx = 0;
        for (JsonNode a : attachments) {
            String attachId = text(a.path("id"));
            if (attachId == null || attachId.isBlank()) attachId = fallbackId + ":att:" + idx;

            String mediaType = text(a.path("mediaType"));
            String type = mapMediaTypeToMastoType(mediaType, text(a.path("type")));

            String url = coalesceUrl(a.path("url"), a.path("href"));
            String previewUrl = coalescePreview(a);
            if (previewUrl == null) previewUrl = url;

            String description = text(a.path("name"));
            if (description == null) description = text(a.path("summary"));

            // meta.original with width/height/aspect/size if available
            int width = a.path("width").asInt(0);
            int height = a.path("height").asInt(0);
            Map<String, Object> meta = Map.of();
            if (width > 0 && height > 0) {
                double aspect = height == 0 ? 0.0 : (double) width / (double) height;
                Map<String, Object> original = new HashMap<>();
                original.put("width", width);
                original.put("height", height);
                original.put("size", width + "x" + height);
                original.put("aspect", aspect);
                Map<String, Object> m = new HashMap<>();
                m.put("original", original);
                meta = m;
            }

            String blurhash = text(a.path("blurhash"));

            list.add(new MediaAttachment(attachId, type, url, previewUrl, url, null, meta, description, blurhash));
            idx++;
        }
        return list;
    }

    private void extractTags(JsonNode tagNode, java.util.List<StatusMention> mentions, java.util.List<StatusTag> tags) {
        if (tagNode == null || !tagNode.isArray()) return;
        for (JsonNode t : tagNode) {
            String tType = text(t.path("type"));
            if ("Mention".equals(tType)) {
                String href = text(t.path("href"));
                String name = text(t.path("name"));
                String acct = name != null && name.startsWith("@") ? name.substring(1) : name;
                String username = acct != null && acct.contains("@") ? acct.substring(0, acct.indexOf('@')) : acct;
                mentions.add(new StatusMention(href, username, href, acct));
            } else if ("Hashtag".equals(tType)) {
                String name = text(t.path("name"));
                String href = text(t.path("href"));
                if (name != null && name.startsWith("#")) name = name.substring(1);
                tags.add(new StatusTag(name, href));
            }
        }
    }

    private String coalesceUrl(JsonNode urlNode, JsonNode hrefNode) {
        String direct = text(urlNode);
        if (direct != null && !direct.isBlank()) return direct;
        if (urlNode != null && urlNode.isArray()) {
            for (JsonNode u : urlNode) {
                String href = text(u.path("href"));
                if (href != null && !href.isBlank()) return href;
                String nested = text(u);
                if (nested != null && !nested.isBlank()) return nested;
            }
        }
        String href = text(hrefNode);
        return href;
    }

    private String coalescePreview(JsonNode attachment) {
        // Some servers expose thumbnails via icon/preview, or in url variants
        String iconUrl = text(attachment.path("icon").path("url"));
        if (iconUrl != null && !iconUrl.isBlank()) return iconUrl;
        if (attachment.path("url").isArray()) {
            for (JsonNode u : attachment.path("url")) {
                String mediaType = text(u.path("mediaType"));
                String href = text(u.path("href"));
                if (mediaType != null && mediaType.startsWith("image/") && href != null && !href.isBlank()) {
                    return href;
                }
            }
        }
        return null;
    }

    private String mapMediaTypeToMastoType(String mediaType, String asType) {
        if (mediaType == null) mediaType = "";
        String lower = mediaType.toLowerCase();
        if (lower.startsWith("image/")) return "image";
        if (lower.equals("image/gif")) return "gifv";
        if (lower.startsWith("video/")) return "video";
        if (lower.startsWith("audio/")) return "audio";
        // fallback based on AS type
        if ("Image".equalsIgnoreCase(asType)) return "image";
        if ("Video".equalsIgnoreCase(asType)) return "video";
        if ("Audio".equalsIgnoreCase(asType)) return "audio";
        return "image";
    }

    private String text(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    public interface AccountSnapshotProvider {
        Mono<edu.sjsu.moth.server.db.Account> getAccountSnapshot(Actor actor);
    }
}
