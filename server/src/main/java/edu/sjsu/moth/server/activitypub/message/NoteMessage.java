package edu.sjsu.moth.server.activitypub.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NoteMessage {

    /**
     * The Note payload itself.
     */
    private String id;
    private String type = "Note";
    private String summary;
    @JsonProperty("inReplyTo")
    private String inReplyTo;
    private String published;
    private String url;
    private String attributedTo;
    private List<String> to;
    private List<String> cc;
    private Boolean sensitive;
    @JsonProperty("atomUri")
    private String atomUri;
    @JsonProperty("inReplyToAtomUri")
    private String inReplyToAtomUri;
    private String conversation;
    private String content;
    private Map<String, String> contentMap;
    private List<Object> attachment;
    private List<Object> tag;
    private Replies replies;

    /**
     * Constructs a NoteMessage with type="Note".
     *
     * @param actor your actor URL (for consistency, though Note itself uses attributedTo)
     */
    public NoteMessage(String id, String summary, String inReplyTo, String published, String url, String attributedTo
            , List<String> to, List<String> cc, Boolean sensitive, String atomUri, String inReplyToAtomUri,
                       String conversation, String content, Map<String, String> contentMap, List<Object> attachment,
                       List<Object> tag, Replies replies) {
        this.id = id;
        this.summary = summary;
        this.inReplyTo = inReplyTo;
        this.published = published;
        this.url = url;
        this.attributedTo = attributedTo;
        this.to = to;
        this.cc = cc;
        this.sensitive = sensitive;
        this.atomUri = atomUri;
        this.inReplyToAtomUri = inReplyToAtomUri;
        this.conversation = conversation;
        this.content = content;
        this.contentMap = contentMap;
        this.attachment = attachment;
        this.tag = tag;
        this.replies = replies;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Replies {
        private String id;
        private String type = "Collection";
        private First first;

        @Data
        @NoArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class First {
            private String type = "CollectionPage";
            private String next;
            @JsonProperty("partOf")
            private String partOf;
            private List<Object> items;
        }
    }
}
