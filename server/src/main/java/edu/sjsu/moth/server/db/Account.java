package edu.sjsu.moth.server.db;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// Definition is https://docs.joinmastodon.org/entities/Account/
@Document("account")
public class Account {
    @Id
    public String id;
    public String username;
    public String acct;
    public String url;
    public String display_name;
    public String note;
    public String avatar;
    public String avatar_static;
    public String header;
    public String header_static;
    public boolean locked;
    public AccountField[] fields;
    public CustomEmoji[] emojis;
    public boolean bot;
    public boolean group;
    public boolean discoverable;
    public boolean noindex;
    public boolean moved;
    public boolean suspended;
    public boolean limited;
    public String created_at;
    public String last_status_at;
    public int statuses_count;
    public int followers_count;
    public int following_count;

    public Account() {}

    public Account(String id, String username, String acct, String url, String display_name, String note,
                   String avatar, String avatar_static, String header, String header_static, boolean locked,
                   AccountField[] fields, CustomEmoji[] emojis, boolean bot, boolean group, boolean discoverable,
                   boolean noindex, boolean moved, boolean suspended, boolean limited, String created_at,
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
        this.noindex = noindex;
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
