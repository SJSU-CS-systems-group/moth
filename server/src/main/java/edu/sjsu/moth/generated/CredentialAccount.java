// THIS FILE WAS GENERATED BY JSON2JAVA
// CHANGES MADE:
//   * use AccountField instead of the generated Field
//   * use CustomEmoji instead of the generated Emoji

package edu.sjsu.moth.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import edu.sjsu.moth.server.db.AccountField;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "id", "username", "acct", "display_name", "locked", "bot", "created_at", "note", "url", "avatar"
        , "avatar_static", "header", "header_static", "followers_count", "following_count", "statuses_count",
        "last_status_at", "source", "emojis", "fields" })
public class CredentialAccount {

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
    @JsonProperty("source")
    public Source source;
    @JsonProperty("emojis")
    public List<CustomEmoji> emojis = new ArrayList<CustomEmoji>();
    @JsonProperty("fields")
    public List<AccountField> fields = new ArrayList<AccountField>();

    /**
     * No args constructor for use in serialization
     */
    public CredentialAccount() {
    }

    public CredentialAccount(String id, String username, String acct, String displayName, Boolean locked, Boolean bot
            , String createdAt, String note, String url, String avatar, String avatarStatic, String header,
                             String headerStatic, Integer followersCount, Integer followingCount,
                             Integer statusesCount, String lastStatusAt, Source source, List<CustomEmoji> emojis,
                             List<AccountField> fields) {
        super();
        this.id = id;
        this.username = username;
        this.acct = acct;
        this.displayName = displayName;
        this.locked = locked;
        this.bot = bot;
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
        this.source = source;
        this.emojis = emojis;
        this.fields = fields;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(CredentialAccount.class.getName())
                .append('@')
                .append(Integer.toHexString(System.identityHashCode(this)))
                .append('[');
        sb.append("id");
        sb.append('=');
        sb.append(((this.id == null) ? "<null>" : this.id));
        sb.append(',');
        sb.append("username");
        sb.append('=');
        sb.append(((this.username == null) ? "<null>" : this.username));
        sb.append(',');
        sb.append("acct");
        sb.append('=');
        sb.append(((this.acct == null) ? "<null>" : this.acct));
        sb.append(',');
        sb.append("displayName");
        sb.append('=');
        sb.append(((this.displayName == null) ? "<null>" : this.displayName));
        sb.append(',');
        sb.append("locked");
        sb.append('=');
        sb.append(((this.locked == null) ? "<null>" : this.locked));
        sb.append(',');
        sb.append("bot");
        sb.append('=');
        sb.append(((this.bot == null) ? "<null>" : this.bot));
        sb.append(',');
        sb.append("createdAt");
        sb.append('=');
        sb.append(((this.createdAt == null) ? "<null>" : this.createdAt));
        sb.append(',');
        sb.append("note");
        sb.append('=');
        sb.append(((this.note == null) ? "<null>" : this.note));
        sb.append(',');
        sb.append("url");
        sb.append('=');
        sb.append(((this.url == null) ? "<null>" : this.url));
        sb.append(',');
        sb.append("avatar");
        sb.append('=');
        sb.append(((this.avatar == null) ? "<null>" : this.avatar));
        sb.append(',');
        sb.append("avatarStatic");
        sb.append('=');
        sb.append(((this.avatarStatic == null) ? "<null>" : this.avatarStatic));
        sb.append(',');
        sb.append("header");
        sb.append('=');
        sb.append(((this.header == null) ? "<null>" : this.header));
        sb.append(',');
        sb.append("headerStatic");
        sb.append('=');
        sb.append(((this.headerStatic == null) ? "<null>" : this.headerStatic));
        sb.append(',');
        sb.append("followersCount");
        sb.append('=');
        sb.append(((this.followersCount == null) ? "<null>" : this.followersCount));
        sb.append(',');
        sb.append("followingCount");
        sb.append('=');
        sb.append(((this.followingCount == null) ? "<null>" : this.followingCount));
        sb.append(',');
        sb.append("statusesCount");
        sb.append('=');
        sb.append(((this.statusesCount == null) ? "<null>" : this.statusesCount));
        sb.append(',');
        sb.append("lastStatusAt");
        sb.append('=');
        sb.append(((this.lastStatusAt == null) ? "<null>" : this.lastStatusAt));
        sb.append(',');
        sb.append("source");
        sb.append('=');
        sb.append(((this.source == null) ? "<null>" : this.source));
        sb.append(',');
        sb.append("emojis");
        sb.append('=');
        sb.append(((this.emojis == null) ? "<null>" : this.emojis));
        sb.append(',');
        sb.append("fields");
        sb.append('=');
        sb.append(((this.fields == null) ? "<null>" : this.fields));
        sb.append(',');
        if (sb.charAt((sb.length() - 1)) == ',') {
            sb.setCharAt((sb.length() - 1), ']');
        } else {
            sb.append(']');
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result * 31) + ((this.emojis == null) ? 0 : this.emojis.hashCode()));
        result = ((result * 31) + ((this.note == null) ? 0 : this.note.hashCode()));
        result = ((result * 31) + ((this.statusesCount == null) ? 0 : this.statusesCount.hashCode()));
        result = ((result * 31) + ((this.displayName == null) ? 0 : this.displayName.hashCode()));
        result = ((result * 31) + ((this.bot == null) ? 0 : this.bot.hashCode()));
        result = ((result * 31) + ((this.avatar == null) ? 0 : this.avatar.hashCode()));
        result = ((result * 31) + ((this.source == null) ? 0 : this.source.hashCode()));
        result = ((result * 31) + ((this.followingCount == null) ? 0 : this.followingCount.hashCode()));
        result = ((result * 31) + ((this.url == null) ? 0 : this.url.hashCode()));
        result = ((result * 31) + ((this.createdAt == null) ? 0 : this.createdAt.hashCode()));
        result = ((result * 31) + ((this.lastStatusAt == null) ? 0 : this.lastStatusAt.hashCode()));
        result = ((result * 31) + ((this.header == null) ? 0 : this.header.hashCode()));
        result = ((result * 31) + ((this.id == null) ? 0 : this.id.hashCode()));
        result = ((result * 31) + ((this.followersCount == null) ? 0 : this.followersCount.hashCode()));
        result = ((result * 31) + ((this.locked == null) ? 0 : this.locked.hashCode()));
        result = ((result * 31) + ((this.avatarStatic == null) ? 0 : this.avatarStatic.hashCode()));
        result = ((result * 31) + ((this.fields == null) ? 0 : this.fields.hashCode()));
        result = ((result * 31) + ((this.acct == null) ? 0 : this.acct.hashCode()));
        result = ((result * 31) + ((this.username == null) ? 0 : this.username.hashCode()));
        result = ((result * 31) + ((this.headerStatic == null) ? 0 : this.headerStatic.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof CredentialAccount rhs)) {
            return false;
        }
        return ((((((((((((((((((((Objects.equals(this.emojis, rhs.emojis)) && (Objects.equals(this.note,
                                                                                               rhs.note))) && (Objects.equals(
                this.statusesCount, rhs.statusesCount))) && (Objects.equals(this.displayName,
                                                                            rhs.displayName))) && (Objects.equals(
                this.bot, rhs.bot))) && (Objects.equals(this.avatar, rhs.avatar))) && (Objects.equals(this.source,
                                                                                                      rhs.source))) && (Objects.equals(
                this.followingCount, rhs.followingCount))) && (Objects.equals(this.url, rhs.url))) && (Objects.equals(
                this.createdAt, rhs.createdAt))) && (Objects.equals(this.lastStatusAt,
                                                                    rhs.lastStatusAt))) && (Objects.equals(this.header,
                                                                                                           rhs.header))) && (Objects.equals(
                this.id, rhs.id))) && (Objects.equals(this.followersCount, rhs.followersCount))) && (Objects.equals(
                this.locked, rhs.locked))) && (Objects.equals(this.avatarStatic, rhs.avatarStatic))) && (Objects.equals(
                this.fields, rhs.fields))) && (Objects.equals(this.acct, rhs.acct))) && (Objects.equals(this.username,
                                                                                                        rhs.username))) && (Objects.equals(
                this.headerStatic, rhs.headerStatic)));
    }

}
