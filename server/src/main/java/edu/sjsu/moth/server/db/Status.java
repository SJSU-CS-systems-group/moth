package edu.sjsu.moth.server.db;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;

// Definition is https://docs.joinmastodon.org/entities/Status/

@Document("status")
public class Status {
    @Id
    public String id;
    public String uri;
    public String created_at;
    public Account account;
    public String content;
    public String visibility;
    public boolean sensitive;
    public String spoiler_text;
    public MediaAttachments[] media_attachments;
    public HashMap<String, String> application;
    public String application_name;
    public String application_website;
    public StatusMention[] mentions;
    public StatusTag[] tags;
    public CustomEmoji[] emojis;
    public int reblogs_count;
    public int favourites_count;
    public int replies_count;

    public String url;
    public String in_reply_to_id;
    public String in_reply_to_account_id;
    public Status reblog;
    public Poll poll;
    public PreviewCard card;
    public String language;
    public String text;
    public String edited_at;
    public boolean favourited;
    public boolean reblogged;
    public boolean muted;
    public boolean bookmarked;
    public boolean pinned;
    public FilterResult[] filtered;

    public Status(String id, String uri, String created_at, Account account, String content, String visibility,
                  boolean sensitive, String spoiler_text, MediaAttachments[] media_attachments, HashMap<String,
            String> application, String application_name, String application_website, StatusMention[] mentions,
                  StatusTag[] tags, CustomEmoji[] emojis, int reblogs_count, int favourites_count, int replies_count,
                  String url, String in_reply_to_id, String in_reply_to_account_id, Status reblog, Poll poll,
                  PreviewCard card, String language, String text, String edited_at, boolean favourited,
                  boolean reblogged, boolean muted, boolean bookmarked, boolean pinned, FilterResult[] filtered) {
        this.id = id;
        this.uri = uri;
        this.created_at = created_at;
        this.account = account;
        this.content = content;
        this.visibility = visibility;
        this.sensitive = sensitive;
        this.spoiler_text = spoiler_text;
        this.media_attachments = media_attachments;
        this.application = application;
        this.application_name = application_name;
        this.application_website = application_website;
        this.mentions = mentions;
        this.tags = tags;
        this.emojis = emojis;
        this.reblogs_count = reblogs_count;
        this.favourites_count = favourites_count;
        this.replies_count = replies_count;
        this.url = url;
        this.in_reply_to_id = in_reply_to_id;
        this.in_reply_to_account_id = in_reply_to_account_id;
        this.reblog = reblog;
        this.poll = poll;
        this.card = card;
        this.language = language;
        this.text = text;
        this.edited_at = edited_at;
        this.favourited = favourited;
        this.reblogged = reblogged;
        this.muted = muted;
        this.bookmarked = bookmarked;
        this.pinned = pinned;
        this.filtered = filtered;
    }
}

