package edu.sjsu.moth.server.db;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.querydsl.core.annotations.QueryEntity;
import edu.sjsu.moth.generated.Application;
import edu.sjsu.moth.generated.Card;
import edu.sjsu.moth.generated.CustomEmoji;
import edu.sjsu.moth.generated.MediaAttachment;
import edu.sjsu.moth.generated.Poll;
import edu.sjsu.moth.generated.Status;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document("ExternalStatus")
@QueryEntity
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExternalStatus extends Status {
    @JsonProperty("uri")
    public String uri;
    @JsonProperty("url")
    public String url;

    public ExternalStatus(String id, String createdAt, String inReplyToId, String inReplyToAccountId,
                          Boolean sensitive, String spoilerText, String visibility, String language, String uri,
                          String url, Integer repliesCount, Integer reblogsCount, Integer favouritesCount,
                          Boolean favourited, Boolean reblogged, Boolean muted, Boolean bookmarked, String content,
                          Status reblog, Application application, Account account,
                          List<MediaAttachment> mediaAttachments, List<StatusMention> mentions, List<StatusTag> tags,
                          List<CustomEmoji> emojis, Card card, Poll poll, String text, String edited_at) {

        super(id, createdAt, inReplyToId, inReplyToAccountId, sensitive, spoilerText, visibility, language, repliesCount, reblogsCount, favouritesCount, favourited, reblogged, muted, bookmarked, content, reblog,
              application, account, mediaAttachments, mentions, tags, emojis, card, poll, text, edited_at);
    }
}