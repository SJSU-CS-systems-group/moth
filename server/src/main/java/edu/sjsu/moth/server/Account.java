package edu.sjsu.moth.server;

import org.springframework.data.annotation.Id;

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
    private final String[] fields;
    private final String[] emojis;
    private final boolean bot;
    private final boolean group;
    private final boolean discoverable;
    private final String created_at;
    private final String last_status_at;

    //private int statuses_count;
    //private int followers_count;
    //private int following_count;

    public Account(String id, String username, String acct, String url, String display_name, String note,
                   String avatar, String avatar_static, String header, String header_static, boolean locked,
                   String[] fields, String[] emojis, boolean bot, boolean group, boolean discoverable,
                   String created_at, String last_status_at) {
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
        this.created_at = created_at;
        this.last_status_at = last_status_at;
    }
}
