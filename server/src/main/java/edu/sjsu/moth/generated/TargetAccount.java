
// THIS FILE WAS GENERATED BY JSON2JAVA
// IT HAS NOT BEEN CHANGED. (IF IT HAS REMOVE THIS LINE)
// CHANGES MADE:
//   * NONE SO FAR


package edu.sjsu.moth.generated;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "id",
    "username",
    "acct",
    "display_name",
    "locked",
    "bot",
    "discoverable",
    "group",
    "created_at",
    "note",
    "url",
    "avatar",
    "avatar_static",
    "header",
    "header_static",
    "followers_count",
    "following_count",
    "statuses_count",
    "last_status_at",
    "emojis",
    "fields"
})
public class TargetAccount {

    @JsonProperty("id")
    public String id;
    @JsonProperty("username")
    public String username;
    @JsonProperty("acct")
    public String acct;
    @JsonProperty("display_name")
    public String displayName;
    @JsonProperty("locked")
    public Boolean locked;
    @JsonProperty("bot")
    public Boolean bot;
    @JsonProperty("discoverable")
    public Boolean discoverable;
    @JsonProperty("group")
    public Boolean group;
    @JsonProperty("created_at")
    public String createdAt;
    @JsonProperty("note")
    public String note;
    @JsonProperty("url")
    public String url;
    @JsonProperty("avatar")
    public String avatar;
    @JsonProperty("avatar_static")
    public String avatarStatic;
    @JsonProperty("header")
    public String header;
    @JsonProperty("header_static")
    public String headerStatic;
    @JsonProperty("followers_count")
    public Integer followersCount;
    @JsonProperty("following_count")
    public Integer followingCount;
    @JsonProperty("statuses_count")
    public Integer statusesCount;
    @JsonProperty("last_status_at")
    public String lastStatusAt;
    @JsonProperty("emojis")
    public List<Object> emojis = new ArrayList<Object>();
    @JsonProperty("fields")
    public List<Object> fields = new ArrayList<Object>();

    /**
     * No args constructor for use in serialization
     * 
     */
    public TargetAccount() {
    }

    public TargetAccount(String id, String username, String acct, String displayName, Boolean locked, Boolean bot, Boolean discoverable, Boolean group, String createdAt, String note, String url, String avatar, String avatarStatic, String header, String headerStatic, Integer followersCount, Integer followingCount, Integer statusesCount, String lastStatusAt, List<Object> emojis, List<Object> fields) {
        super();
        this.id = id;
        this.username = username;
        this.acct = acct;
        this.displayName = displayName;
        this.locked = locked;
        this.bot = bot;
        this.discoverable = discoverable;
        this.group = group;
        this.createdAt = createdAt;
        this.note = note;
        this.url = url;
        this.avatar = avatar;
        this.avatarStatic = avatarStatic;
        this.header = header;
        this.headerStatic = headerStatic;
        this.followersCount = followersCount;
        this.followingCount = followingCount;
        this.statusesCount = statusesCount;
        this.lastStatusAt = lastStatusAt;
        this.emojis = emojis;
        this.fields = fields;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(TargetAccount.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("id");
        sb.append('=');
        sb.append(((this.id == null)?"<null>":this.id));
        sb.append(',');
        sb.append("username");
        sb.append('=');
        sb.append(((this.username == null)?"<null>":this.username));
        sb.append(',');
        sb.append("acct");
        sb.append('=');
        sb.append(((this.acct == null)?"<null>":this.acct));
        sb.append(',');
        sb.append("displayName");
        sb.append('=');
        sb.append(((this.displayName == null)?"<null>":this.displayName));
        sb.append(',');
        sb.append("locked");
        sb.append('=');
        sb.append(((this.locked == null)?"<null>":this.locked));
        sb.append(',');
        sb.append("bot");
        sb.append('=');
        sb.append(((this.bot == null)?"<null>":this.bot));
        sb.append(',');
        sb.append("discoverable");
        sb.append('=');
        sb.append(((this.discoverable == null)?"<null>":this.discoverable));
        sb.append(',');
        sb.append("group");
        sb.append('=');
        sb.append(((this.group == null)?"<null>":this.group));
        sb.append(',');
        sb.append("createdAt");
        sb.append('=');
        sb.append(((this.createdAt == null)?"<null>":this.createdAt));
        sb.append(',');
        sb.append("note");
        sb.append('=');
        sb.append(((this.note == null)?"<null>":this.note));
        sb.append(',');
        sb.append("url");
        sb.append('=');
        sb.append(((this.url == null)?"<null>":this.url));
        sb.append(',');
        sb.append("avatar");
        sb.append('=');
        sb.append(((this.avatar == null)?"<null>":this.avatar));
        sb.append(',');
        sb.append("avatarStatic");
        sb.append('=');
        sb.append(((this.avatarStatic == null)?"<null>":this.avatarStatic));
        sb.append(',');
        sb.append("header");
        sb.append('=');
        sb.append(((this.header == null)?"<null>":this.header));
        sb.append(',');
        sb.append("headerStatic");
        sb.append('=');
        sb.append(((this.headerStatic == null)?"<null>":this.headerStatic));
        sb.append(',');
        sb.append("followersCount");
        sb.append('=');
        sb.append(((this.followersCount == null)?"<null>":this.followersCount));
        sb.append(',');
        sb.append("followingCount");
        sb.append('=');
        sb.append(((this.followingCount == null)?"<null>":this.followingCount));
        sb.append(',');
        sb.append("statusesCount");
        sb.append('=');
        sb.append(((this.statusesCount == null)?"<null>":this.statusesCount));
        sb.append(',');
        sb.append("lastStatusAt");
        sb.append('=');
        sb.append(((this.lastStatusAt == null)?"<null>":this.lastStatusAt));
        sb.append(',');
        sb.append("emojis");
        sb.append('=');
        sb.append(((this.emojis == null)?"<null>":this.emojis));
        sb.append(',');
        sb.append("fields");
        sb.append('=');
        sb.append(((this.fields == null)?"<null>":this.fields));
        sb.append(',');
        if (sb.charAt((sb.length()- 1)) == ',') {
            sb.setCharAt((sb.length()- 1), ']');
        } else {
            sb.append(']');
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.emojis == null)? 0 :this.emojis.hashCode()));
        result = ((result* 31)+((this.note == null)? 0 :this.note.hashCode()));
        result = ((result* 31)+((this.statusesCount == null)? 0 :this.statusesCount.hashCode()));
        result = ((result* 31)+((this.displayName == null)? 0 :this.displayName.hashCode()));
        result = ((result* 31)+((this.bot == null)? 0 :this.bot.hashCode()));
        result = ((result* 31)+((this.avatar == null)? 0 :this.avatar.hashCode()));
        result = ((result* 31)+((this.followingCount == null)? 0 :this.followingCount.hashCode()));
        result = ((result* 31)+((this.url == null)? 0 :this.url.hashCode()));
        result = ((result* 31)+((this.createdAt == null)? 0 :this.createdAt.hashCode()));
        result = ((result* 31)+((this.discoverable == null)? 0 :this.discoverable.hashCode()));
        result = ((result* 31)+((this.lastStatusAt == null)? 0 :this.lastStatusAt.hashCode()));
        result = ((result* 31)+((this.header == null)? 0 :this.header.hashCode()));
        result = ((result* 31)+((this.id == null)? 0 :this.id.hashCode()));
        result = ((result* 31)+((this.followersCount == null)? 0 :this.followersCount.hashCode()));
        result = ((result* 31)+((this.locked == null)? 0 :this.locked.hashCode()));
        result = ((result* 31)+((this.avatarStatic == null)? 0 :this.avatarStatic.hashCode()));
        result = ((result* 31)+((this.fields == null)? 0 :this.fields.hashCode()));
        result = ((result* 31)+((this.acct == null)? 0 :this.acct.hashCode()));
        result = ((result* 31)+((this.username == null)? 0 :this.username.hashCode()));
        result = ((result* 31)+((this.group == null)? 0 :this.group.hashCode()));
        result = ((result* 31)+((this.headerStatic == null)? 0 :this.headerStatic.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof TargetAccount) == false) {
            return false;
        }
        TargetAccount rhs = ((TargetAccount) other);
        return ((((((((((((((((((((((this.emojis == rhs.emojis)||((this.emojis!= null)&&this.emojis.equals(rhs.emojis)))&&((this.note == rhs.note)||((this.note!= null)&&this.note.equals(rhs.note))))&&((this.statusesCount == rhs.statusesCount)||((this.statusesCount!= null)&&this.statusesCount.equals(rhs.statusesCount))))&&((this.displayName == rhs.displayName)||((this.displayName!= null)&&this.displayName.equals(rhs.displayName))))&&((this.bot == rhs.bot)||((this.bot!= null)&&this.bot.equals(rhs.bot))))&&((this.avatar == rhs.avatar)||((this.avatar!= null)&&this.avatar.equals(rhs.avatar))))&&((this.followingCount == rhs.followingCount)||((this.followingCount!= null)&&this.followingCount.equals(rhs.followingCount))))&&((this.url == rhs.url)||((this.url!= null)&&this.url.equals(rhs.url))))&&((this.createdAt == rhs.createdAt)||((this.createdAt!= null)&&this.createdAt.equals(rhs.createdAt))))&&((this.discoverable == rhs.discoverable)||((this.discoverable!= null)&&this.discoverable.equals(rhs.discoverable))))&&((this.lastStatusAt == rhs.lastStatusAt)||((this.lastStatusAt!= null)&&this.lastStatusAt.equals(rhs.lastStatusAt))))&&((this.header == rhs.header)||((this.header!= null)&&this.header.equals(rhs.header))))&&((this.id == rhs.id)||((this.id!= null)&&this.id.equals(rhs.id))))&&((this.followersCount == rhs.followersCount)||((this.followersCount!= null)&&this.followersCount.equals(rhs.followersCount))))&&((this.locked == rhs.locked)||((this.locked!= null)&&this.locked.equals(rhs.locked))))&&((this.avatarStatic == rhs.avatarStatic)||((this.avatarStatic!= null)&&this.avatarStatic.equals(rhs.avatarStatic))))&&((this.fields == rhs.fields)||((this.fields!= null)&&this.fields.equals(rhs.fields))))&&((this.acct == rhs.acct)||((this.acct!= null)&&this.acct.equals(rhs.acct))))&&((this.username == rhs.username)||((this.username!= null)&&this.username.equals(rhs.username))))&&((this.group == rhs.group)||((this.group!= null)&&this.group.equals(rhs.group))))&&((this.headerStatic == rhs.headerStatic)||((this.headerStatic!= null)&&this.headerStatic.equals(rhs.headerStatic))));
    }

}
