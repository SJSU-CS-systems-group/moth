// THIS FILE WAS GENERATED BY JSON2JAVA
// IT HAS NOT BEEN CHANGED. (IF IT HAS REMOVE THIS LINE)
// CHANGES MADE:
//   * NONE SO FAR

package edu.sjsu.moth.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "shortcode", "url", "static_url", "visible_in_picker", "category" })
public class CustomEmoji {

    @JsonProperty("shortcode")
    public String shortcode;
    @JsonProperty("url")
    public String url;
    @JsonProperty("static_url")
    public String staticUrl;
    @JsonProperty("visible_in_picker")
    public Boolean visibleInPicker;
    @JsonProperty("category")
    public String category;

    /**
     * No args constructor for use in serialization
     */
    public CustomEmoji() {
    }

    public CustomEmoji(String shortcode, String url, String staticUrl, Boolean visibleInPicker, String category) {
        super();
        this.shortcode = shortcode;
        this.url = url;
        this.staticUrl = staticUrl;
        this.visibleInPicker = visibleInPicker;
        this.category = category;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(CustomEmoji.class.getName())
                .append('@')
                .append(Integer.toHexString(System.identityHashCode(this)))
                .append('[');
        sb.append("shortcode");
        sb.append('=');
        sb.append(((this.shortcode == null) ? "<null>" : this.shortcode));
        sb.append(',');
        sb.append("url");
        sb.append('=');
        sb.append(((this.url == null) ? "<null>" : this.url));
        sb.append(',');
        sb.append("staticUrl");
        sb.append('=');
        sb.append(((this.staticUrl == null) ? "<null>" : this.staticUrl));
        sb.append(',');
        sb.append("visibleInPicker");
        sb.append('=');
        sb.append(((this.visibleInPicker == null) ? "<null>" : this.visibleInPicker));
        sb.append(',');
        sb.append("category");
        sb.append('=');
        sb.append(((this.category == null) ? "<null>" : this.category));
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
        result = ((result * 31) + ((this.staticUrl == null) ? 0 : this.staticUrl.hashCode()));
        result = ((result * 31) + ((this.shortcode == null) ? 0 : this.shortcode.hashCode()));
        result = ((result * 31) + ((this.category == null) ? 0 : this.category.hashCode()));
        result = ((result * 31) + ((this.visibleInPicker == null) ? 0 : this.visibleInPicker.hashCode()));
        result = ((result * 31) + ((this.url == null) ? 0 : this.url.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof CustomEmoji)) {
            return false;
        }
        CustomEmoji rhs = ((CustomEmoji) other);
        return (((((Objects.equals(this.staticUrl, rhs.staticUrl)) && (Objects.equals(this.shortcode, rhs.shortcode))) && (Objects.equals(this.category, rhs.category))) && (Objects.equals(this.visibleInPicker, rhs.visibleInPicker))) && (Objects.equals(this.url, rhs.url)));
    }

}
