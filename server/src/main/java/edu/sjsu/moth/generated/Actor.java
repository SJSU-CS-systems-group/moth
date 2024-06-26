// THIS FILE WAS GENERATED BY JSON2JAVA
// IT HAS NOT BEEN CHANGED. (IF IT HAS REMOVE THIS LINE)
// CHANGES MADE:
//   Removed @Context, Added @QueryEntity and @Document("Actor")
// Added Actor to Account Method temporary to test functionality

package edu.sjsu.moth.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.querydsl.core.annotations.QueryEntity;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "id", "type", "following", "followers", "inbox", "outbox", "featured", "featuredTags",
        "preferredUsername", "name", "summary", "url", "manuallyApprovesFollowers", "discoverable", "indexable",
        "published", "memorial", "devices", "publicKey", "tag", "attachment", "endpoints", "icon", "image" })
@QueryEntity
@Document("Actor")
public class Actor {
    @JsonProperty("id")
    public String id;
    @JsonProperty("type")
    public String type;
    @JsonProperty("following")
    public String following;
    @JsonProperty("followers")
    public String followers;
    @JsonProperty("inbox")
    public String inbox;
    @JsonProperty("outbox")
    public String outbox;
    @JsonProperty("featured")
    public String featured;
    @JsonProperty("featuredTags")
    public String featuredTags;
    @JsonProperty("preferredUsername")
    public String preferredUsername;
    @JsonProperty("name")
    public String name;
    @JsonProperty("summary")
    public String summary;
    @JsonProperty("url")
    public String url;
    @JsonProperty("manuallyApprovesFollowers")
    public Boolean manuallyApprovesFollowers;
    @JsonProperty("discoverable")
    public Boolean discoverable;
    @JsonProperty("indexable")
    public Boolean indexable;
    @JsonProperty("published")
    public String published;
    @JsonProperty("memorial")
    public Boolean memorial;
    @JsonProperty("devices")
    public String devices;
    @JsonProperty("publicKey")
    public PublicKey publicKey;
    @JsonProperty("tag")
    public List<Tag> tag = new ArrayList<Tag>();
    @JsonProperty("attachment")
    public List<Attachment> attachment = new ArrayList<Attachment>();
    @JsonProperty("endpoints")
    public Endpoints endpoints;
    @JsonProperty("icon")
    public Icon icon;
    @JsonProperty("image")
    public Image image;

    /**
     * No args constructor for use in serialization
     */
    public Actor() {
    }

    public Actor(String id, String type, String following, String followers, String inbox, String outbox,
                 String featured, String featuredTags, String preferredUsername, String name, String summary,
                 String url, Boolean manuallyApprovesFollowers, Boolean discoverable, Boolean indexable,
                 String published, Boolean memorial, String devices, PublicKey publicKey, List<Tag> tag,
                 List<Attachment> attachment, Endpoints endpoints, Icon icon, Image image) {
        super();
        this.id = id;
        this.type = type;
        this.following = following;
        this.followers = followers;
        this.inbox = inbox;
        this.outbox = outbox;
        this.featured = featured;
        this.featuredTags = featuredTags;
        this.preferredUsername = preferredUsername;
        this.name = name;
        this.summary = summary;
        this.url = url;
        this.manuallyApprovesFollowers = manuallyApprovesFollowers;
        this.discoverable = discoverable;
        this.indexable = indexable;
        this.published = published;
        this.memorial = memorial;
        this.devices = devices;
        this.publicKey = publicKey;
        this.tag = tag;
        this.attachment = attachment;
        this.endpoints = endpoints;
        this.icon = icon;
        this.image = image;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Actor.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this)))
                .append('[');
        sb.append(',');
        sb.append("id");
        sb.append('=');
        sb.append(((this.id == null) ? "<null>" : this.id));
        sb.append(',');
        sb.append("type");
        sb.append('=');
        sb.append(((this.type == null) ? "<null>" : this.type));
        sb.append(',');
        sb.append("following");
        sb.append('=');
        sb.append(((this.following == null) ? "<null>" : this.following));
        sb.append(',');
        sb.append("followers");
        sb.append('=');
        sb.append(((this.followers == null) ? "<null>" : this.followers));
        sb.append(',');
        sb.append("inbox");
        sb.append('=');
        sb.append(((this.inbox == null) ? "<null>" : this.inbox));
        sb.append(',');
        sb.append("outbox");
        sb.append('=');
        sb.append(((this.outbox == null) ? "<null>" : this.outbox));
        sb.append(',');
        sb.append("featured");
        sb.append('=');
        sb.append(((this.featured == null) ? "<null>" : this.featured));
        sb.append(',');
        sb.append("featuredTags");
        sb.append('=');
        sb.append(((this.featuredTags == null) ? "<null>" : this.featuredTags));
        sb.append(',');
        sb.append("preferredUsername");
        sb.append('=');
        sb.append(((this.preferredUsername == null) ? "<null>" : this.preferredUsername));
        sb.append(',');
        sb.append("name");
        sb.append('=');
        sb.append(((this.name == null) ? "<null>" : this.name));
        sb.append(',');
        sb.append("summary");
        sb.append('=');
        sb.append(((this.summary == null) ? "<null>" : this.summary));
        sb.append(',');
        sb.append("url");
        sb.append('=');
        sb.append(((this.url == null) ? "<null>" : this.url));
        sb.append(',');
        sb.append("manuallyApprovesFollowers");
        sb.append('=');
        sb.append(((this.manuallyApprovesFollowers == null) ? "<null>" : this.manuallyApprovesFollowers));
        sb.append(',');
        sb.append("discoverable");
        sb.append('=');
        sb.append(((this.discoverable == null) ? "<null>" : this.discoverable));
        sb.append(',');
        sb.append("indexable");
        sb.append('=');
        sb.append(((this.indexable == null) ? "<null>" : this.indexable));
        sb.append(',');
        sb.append("published");
        sb.append('=');
        sb.append(((this.published == null) ? "<null>" : this.published));
        sb.append(',');
        sb.append("memorial");
        sb.append('=');
        sb.append(((this.memorial == null) ? "<null>" : this.memorial));
        sb.append(',');
        sb.append("devices");
        sb.append('=');
        sb.append(((this.devices == null) ? "<null>" : this.devices));
        sb.append(',');
        sb.append("publicKey");
        sb.append('=');
        sb.append(((this.publicKey == null) ? "<null>" : this.publicKey));
        sb.append(',');
        sb.append("tag");
        sb.append('=');
        sb.append(((this.tag == null) ? "<null>" : this.tag));
        sb.append(',');
        sb.append("attachment");
        sb.append('=');
        sb.append(((this.attachment == null) ? "<null>" : this.attachment));
        sb.append(',');
        sb.append("endpoints");
        sb.append('=');
        sb.append(((this.endpoints == null) ? "<null>" : this.endpoints));
        sb.append(',');
        sb.append("icon");
        sb.append('=');
        sb.append(((this.icon == null) ? "<null>" : this.icon));
        sb.append(',');
        sb.append("image");
        sb.append('=');
        sb.append(((this.image == null) ? "<null>" : this.image));
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
        result = ((result * 31) + ((this.featured == null) ? 0 : this.featured.hashCode()));
        result = ((result * 31) +
                ((this.manuallyApprovesFollowers == null) ? 0 : this.manuallyApprovesFollowers.hashCode()));
        result = ((result * 31) + ((this.indexable == null) ? 0 : this.indexable.hashCode()));
        result = ((result * 31) + ((this.icon == null) ? 0 : this.icon.hashCode()));
        result = ((result * 31) + ((this.publicKey == null) ? 0 : this.publicKey.hashCode()));
        result = ((result * 31) + ((this.type == null) ? 0 : this.type.hashCode()));
        result = ((result * 31) + ((this.attachment == null) ? 0 : this.attachment.hashCode()));
        result = ((result * 31) + ((this.id == null) ? 0 : this.id.hashCode()));
        result = ((result * 31) + ((this.memorial == null) ? 0 : this.memorial.hashCode()));
        result = ((result * 31) + ((this.tag == null) ? 0 : this.tag.hashCode()));
        result = ((result * 31) + ((this.summary == null) ? 0 : this.summary.hashCode()));
        result = ((result * 31) + ((this.image == null) ? 0 : this.image.hashCode()));
        result = ((result * 31) + ((this.endpoints == null) ? 0 : this.endpoints.hashCode()));
        result = ((result * 31) + ((this.preferredUsername == null) ? 0 : this.preferredUsername.hashCode()));
        result = ((result * 31) + ((this.devices == null) ? 0 : this.devices.hashCode()));
        result = ((result * 31) + ((this.published == null) ? 0 : this.published.hashCode()));
        result = ((result * 31) + ((this.outbox == null) ? 0 : this.outbox.hashCode()));
        result = ((result * 31) + ((this.featuredTags == null) ? 0 : this.featuredTags.hashCode()));
        result = ((result * 31) + ((this.url == null) ? 0 : this.url.hashCode()));
        result = ((result * 31) + ((this.followers == null) ? 0 : this.followers.hashCode()));
        result = ((result * 31) + ((this.discoverable == null) ? 0 : this.discoverable.hashCode()));
        result = ((result * 31) + ((this.following == null) ? 0 : this.following.hashCode()));
        result = ((result * 31) + ((this.name == null) ? 0 : this.name.hashCode()));
        result = ((result * 31) + ((this.inbox == null) ? 0 : this.inbox.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof Actor)) {
            return false;
        }
        Actor rhs = ((Actor) other);
        return (((((((((((((((((((((((((Objects.equals(this.featured, rhs.featured)) &&
                (Objects.equals(this.manuallyApprovesFollowers, rhs.manuallyApprovesFollowers))) &&
                (Objects.equals(this.indexable, rhs.indexable))) && (Objects.equals(this.icon, rhs.icon))) &&
                (Objects.equals(this.publicKey, rhs.publicKey))) && (Objects.equals(this.type, rhs.type))) &&
                (Objects.equals(this.attachment, rhs.attachment)))) && (Objects.equals(this.id, rhs.id))) &&
                (Objects.equals(this.memorial, rhs.memorial))) && (Objects.equals(this.tag, rhs.tag))) &&
                (Objects.equals(this.summary, rhs.summary))) && (Objects.equals(this.image, rhs.image))) &&
                (Objects.equals(this.endpoints, rhs.endpoints))) &&
                (Objects.equals(this.preferredUsername, rhs.preferredUsername))) &&
                (Objects.equals(this.devices, rhs.devices))) && (Objects.equals(this.published, rhs.published))) &&
                (Objects.equals(this.outbox, rhs.outbox))) && (Objects.equals(this.featuredTags, rhs.featuredTags))) &&
                (Objects.equals(this.url, rhs.url))) && (Objects.equals(this.followers, rhs.followers))) &&
                (Objects.equals(this.discoverable, rhs.discoverable))) &&
                (Objects.equals(this.following, rhs.following))) && (Objects.equals(this.name, rhs.name))) &&
                (Objects.equals(this.inbox, rhs.inbox)));
    }
}