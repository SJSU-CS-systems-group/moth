package edu.sjsu.moth.server.db;

import com.querydsl.core.annotations.QueryEntity;
import edu.sjsu.moth.generated.CustomEmoji;
import edu.sjsu.moth.server.controller.MothController;
import edu.sjsu.moth.server.util.Util;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@QueryEntity
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
    public List<AccountField> fields;
    public CustomEmoji[] emojis;
    public boolean bot;
    public boolean group;
    public boolean discoverable;
    public boolean noindex;
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
                   List<AccountField> fields, CustomEmoji[] emojis, boolean bot, boolean group, boolean discoverable,
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
        // moved is not a boolean. it's the new account. removing for now
        //this.moved = moved;
        this.suspended = suspended;
        this.limited = limited;
        this.created_at = created_at;
        this.last_status_at = last_status_at;
        this.statuses_count = statuses_count;
        this.followers_count = followers_count;
        this.following_count = following_count;
    }

    public Account(String username) {
        this(username, username, username, MothController.BASE_URL + "/@" + username, "", "", "", "", "", "", false,
             new ArrayList<AccountField>(), new CustomEmoji[0], false, false, false, false, false, false, false, Util.now(),
             Util.now(), 0, 0, 0);
    }

    public void setNote(String note) {
        this.note = note;
    }

    public void setDisplayName(String display_name) {
        this.display_name = display_name;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public void setBot(boolean bot) {
        this.bot = bot;
    }

    public void setDiscoverable(boolean discoverable) {
        this.discoverable = discoverable;
    }

    public void setFields(List<AccountField> fields) {
        this.fields.addAll(fields);
    }

}
