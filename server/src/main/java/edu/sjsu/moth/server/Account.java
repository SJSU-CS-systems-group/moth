package edu.sjsu.moth.server;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// Definition is https://docs.joinmastodon.org/entities/Account/
@Document("account")
public class Account {
    @Id
    private final String id;
    private final String username;
    private final String acct;
    private final String url;
    private final String display_name;
    private final String note;
    private final String avatar;
    private final String avatar_static;
    private final String header;
    private final String header_static;
    private final boolean locked;
    private final AccountField[] fields;
    private final CustomEmoji[] emojis;
    private final boolean bot;
    private final boolean group;
    private final boolean discoverable;
    private final boolean noIndex;
    private final boolean moved;
    private final boolean suspended;
    private final boolean limited;
    private final String created_at;
    private final String last_status_at;
    private final int statuses_count;
    private final int followers_count;
    private final int following_count;

    public Account(String id, String username, String acct, String url, String display_name, String note,
                   String avatar, String avatar_static, String header, String header_static, boolean locked,
                   AccountField[] fields, CustomEmoji[] emojis, boolean bot, boolean group, boolean discoverable,
                   boolean noIndex, boolean moved, boolean suspended, boolean limited, String created_at,
                   String last_status_at, int statuses_count, int followers_count, int following_count) {
        this.id = id;
        this.username = username;
        this.acct = acct;
        this.url = url;
        this.note = note;
        this.display_name = display_name;
        this.avatar = avatar;
        this.avatar_static = avatar_static;
        this.header = header;
        this.header_static = header_static;
        this.locked = locked;
        this.fields = fields;
        this.emojis = emojis;
        this.bot = bot;
        this.group = group;
        this.discoverable = discoverable;
        this.noIndex = noIndex;
        this.moved = moved;
        this.suspended = suspended;
        this.limited = limited;
        this.created_at = created_at;
        this.last_status_at = last_status_at;
        this.statuses_count = statuses_count;
        this.followers_count = followers_count;
        this.following_count = following_count;
    }
}
