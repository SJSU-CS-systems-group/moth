// THIS FILE WAS GENERATED BY JSON2JAVA
// CHANGES MADE:
//   * remoteUrl changed to String type from Object type
//   * description changed to String type

package edu.sjsu.moth.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Map;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "id", "type", "url", "preview_url", "remote_url", "text_url", "meta", "description", "blurhash" })
public class MediaAttachment {

    @JsonProperty("id")
    public String id;
    @JsonProperty("type")
    public String type;
    @JsonProperty("url")
    public String url;
    @JsonProperty("preview_url")
    public String previewUrl;
    @JsonProperty("remote_url")
    public String remoteUrl;
    @JsonProperty("text_url")
    public String textUrl;
    @JsonProperty("meta")
    public Map<String, Object> meta;
    @JsonProperty("description")
    public String description;
    @JsonProperty("blurhash")
    public String blurhash;

    /**
     * No args constructor for use in serialization
     */
    public MediaAttachment() {
    }

    public MediaAttachment(String id, String type, String url, String previewUrl, String remoteUrl, String textUrl,
                           Map<String, Object> meta, String description, String blurhash) {
        super();
        this.id = id;
        this.type = type;
        this.url = url;
        this.previewUrl = previewUrl;
        this.remoteUrl = remoteUrl;
        this.textUrl = textUrl;
        this.meta = meta;
        this.description = description;
        this.blurhash = blurhash;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(MediaAttachment.class.getName()).append('@')
                .append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("id");
        sb.append('=');
        sb.append(((this.id == null) ? "<null>" : this.id));
        sb.append(',');
        sb.append("type");
        sb.append('=');
        sb.append(((this.type == null) ? "<null>" : this.type));
        sb.append(',');
        sb.append("url");
        sb.append('=');
        sb.append(((this.url == null) ? "<null>" : this.url));
        sb.append(',');
        sb.append("previewUrl");
        sb.append('=');
        sb.append(((this.previewUrl == null) ? "<null>" : this.previewUrl));
        sb.append(',');
        sb.append("remoteUrl");
        sb.append('=');
        sb.append(((this.remoteUrl == null) ? "<null>" : this.remoteUrl));
        sb.append(',');
        sb.append("textUrl");
        sb.append('=');
        sb.append(((this.textUrl == null) ? "<null>" : this.textUrl));
        sb.append(',');
        sb.append("meta");
        sb.append('=');
        sb.append(((this.meta == null) ? "<null>" : this.meta));
        sb.append(',');
        sb.append("description");
        sb.append('=');
        sb.append(((this.description == null) ? "<null>" : this.description));
        sb.append(',');
        sb.append("blurhash");
        sb.append('=');
        sb.append(((this.blurhash == null) ? "<null>" : this.blurhash));
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
        result = ((result * 31) + ((this.previewUrl == null) ? 0 : this.previewUrl.hashCode()));
        result = ((result * 31) + ((this.meta == null) ? 0 : this.meta.hashCode()));
        result = ((result * 31) + ((this.blurhash == null) ? 0 : this.blurhash.hashCode()));
        result = ((result * 31) + ((this.description == null) ? 0 : this.description.hashCode()));
        result = ((result * 31) + ((this.remoteUrl == null) ? 0 : this.remoteUrl.hashCode()));
        result = ((result * 31) + ((this.id == null) ? 0 : this.id.hashCode()));
        result = ((result * 31) + ((this.type == null) ? 0 : this.type.hashCode()));
        result = ((result * 31) + ((this.url == null) ? 0 : this.url.hashCode()));
        result = ((result * 31) + ((this.textUrl == null) ? 0 : this.textUrl.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof MediaAttachment)) {
            return false;
        }
        MediaAttachment rhs = ((MediaAttachment) other);
        return (((((((((Objects.equals(this.previewUrl, rhs.previewUrl)) && (Objects.equals(this.meta, rhs.meta))) &&
                (Objects.equals(this.blurhash, rhs.blurhash))) &&
                (Objects.equals(this.description, rhs.description))) &&
                (Objects.equals(this.remoteUrl, rhs.remoteUrl))) && (Objects.equals(this.id, rhs.id))) &&
                (Objects.equals(this.type, rhs.type))) && (Objects.equals(this.url, rhs.url))) &&
                (Objects.equals(this.textUrl, rhs.textUrl)));
    }

}