// THIS FILE WAS GENERATED BY JSON2JAVA
// IT HAS NOT BEEN CHANGED. (IF IT HAS REMOVE THIS LINE)
// CHANGES MADE:
//   * NONE SO FAR

package edu.sjsu.moth.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "url", "title", "description", "type", "author_name", "author_url", "provider_name",
        "provider_url", "html", "width", "height", "image", "embed_url", "blurhash" })
public class Card {

    @JsonProperty("url")
    public String url;
    @JsonProperty("title")
    public String title;
    @JsonProperty("description")
    public String description;
    @JsonProperty("type")
    public String type;
    @JsonProperty("author_name")
    public String authorName;
    @JsonProperty("author_url")
    public String authorUrl;
    @JsonProperty("provider_name")
    public String providerName;
    @JsonProperty("provider_url")
    public String providerUrl;
    @JsonProperty("html")
    public String html;
    @JsonProperty("width")
    public Integer width;
    @JsonProperty("height")
    public Integer height;
    @JsonProperty("image")
    public String image;
    @JsonProperty("embed_url")
    public String embedUrl;
    @JsonProperty("blurhash")
    public String blurhash;

    /**
     * No args constructor for use in serialization
     */
    public Card() {
    }

    public Card(String url, String title, String description, String type, String authorName, String authorUrl,
                String providerName, String providerUrl, String html, Integer width, Integer height, String image,
                String embedUrl, String blurhash) {
        super();
        this.url = url;
        this.title = title;
        this.description = description;
        this.type = type;
        this.authorName = authorName;
        this.authorUrl = authorUrl;
        this.providerName = providerName;
        this.providerUrl = providerUrl;
        this.html = html;
        this.width = width;
        this.height = height;
        this.image = image;
        this.embedUrl = embedUrl;
        this.blurhash = blurhash;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Card.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this)))
                .append('[');
        sb.append("url");
        sb.append('=');
        sb.append(((this.url == null) ? "<null>" : this.url));
        sb.append(',');
        sb.append("title");
        sb.append('=');
        sb.append(((this.title == null) ? "<null>" : this.title));
        sb.append(',');
        sb.append("description");
        sb.append('=');
        sb.append(((this.description == null) ? "<null>" : this.description));
        sb.append(',');
        sb.append("type");
        sb.append('=');
        sb.append(((this.type == null) ? "<null>" : this.type));
        sb.append(',');
        sb.append("authorName");
        sb.append('=');
        sb.append(((this.authorName == null) ? "<null>" : this.authorName));
        sb.append(',');
        sb.append("authorUrl");
        sb.append('=');
        sb.append(((this.authorUrl == null) ? "<null>" : this.authorUrl));
        sb.append(',');
        sb.append("providerName");
        sb.append('=');
        sb.append(((this.providerName == null) ? "<null>" : this.providerName));
        sb.append(',');
        sb.append("providerUrl");
        sb.append('=');
        sb.append(((this.providerUrl == null) ? "<null>" : this.providerUrl));
        sb.append(',');
        sb.append("html");
        sb.append('=');
        sb.append(((this.html == null) ? "<null>" : this.html));
        sb.append(',');
        sb.append("width");
        sb.append('=');
        sb.append(((this.width == null) ? "<null>" : this.width));
        sb.append(',');
        sb.append("height");
        sb.append('=');
        sb.append(((this.height == null) ? "<null>" : this.height));
        sb.append(',');
        sb.append("image");
        sb.append('=');
        sb.append(((this.image == null) ? "<null>" : this.image));
        sb.append(',');
        sb.append("embedUrl");
        sb.append('=');
        sb.append(((this.embedUrl == null) ? "<null>" : this.embedUrl));
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
        result = ((result * 31) + ((this.embedUrl == null) ? 0 : this.embedUrl.hashCode()));
        result = ((result * 31) + ((this.image == null) ? 0 : this.image.hashCode()));
        result = ((result * 31) + ((this.blurhash == null) ? 0 : this.blurhash.hashCode()));
        result = ((result * 31) + ((this.description == null) ? 0 : this.description.hashCode()));
        result = ((result * 31) + ((this.title == null) ? 0 : this.title.hashCode()));
        result = ((result * 31) + ((this.type == null) ? 0 : this.type.hashCode()));
        result = ((result * 31) + ((this.url == null) ? 0 : this.url.hashCode()));
        result = ((result * 31) + ((this.providerUrl == null) ? 0 : this.providerUrl.hashCode()));
        result = ((result * 31) + ((this.authorName == null) ? 0 : this.authorName.hashCode()));
        result = ((result * 31) + ((this.authorUrl == null) ? 0 : this.authorUrl.hashCode()));
        result = ((result * 31) + ((this.width == null) ? 0 : this.width.hashCode()));
        result = ((result * 31) + ((this.html == null) ? 0 : this.html.hashCode()));
        result = ((result * 31) + ((this.providerName == null) ? 0 : this.providerName.hashCode()));
        result = ((result * 31) + ((this.height == null) ? 0 : this.height.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Card) == false) {
            return false;
        }
        Card rhs = ((Card) other);
        return (((((((((((((((this.embedUrl == rhs.embedUrl) ||
                ((this.embedUrl != null) && this.embedUrl.equals(rhs.embedUrl))) &&
                ((this.image == rhs.image) || ((this.image != null) && this.image.equals(rhs.image)))) &&
                ((this.blurhash == rhs.blurhash) || ((this.blurhash != null) && this.blurhash.equals(rhs.blurhash)))) &&
                ((this.description == rhs.description) ||
                        ((this.description != null) && this.description.equals(rhs.description)))) &&
                ((this.title == rhs.title) || ((this.title != null) && this.title.equals(rhs.title)))) &&
                ((this.type == rhs.type) || ((this.type != null) && this.type.equals(rhs.type)))) &&
                ((this.url == rhs.url) || ((this.url != null) && this.url.equals(rhs.url)))) &&
                ((this.providerUrl == rhs.providerUrl) ||
                        ((this.providerUrl != null) && this.providerUrl.equals(rhs.providerUrl)))) &&
                ((this.authorName == rhs.authorName) ||
                        ((this.authorName != null) && this.authorName.equals(rhs.authorName)))) &&
                ((this.authorUrl == rhs.authorUrl) ||
                        ((this.authorUrl != null) && this.authorUrl.equals(rhs.authorUrl)))) &&
                ((this.width == rhs.width) || ((this.width != null) && this.width.equals(rhs.width)))) &&
                ((this.html == rhs.html) || ((this.html != null) && this.html.equals(rhs.html)))) &&
                ((this.providerName == rhs.providerName) ||
                        ((this.providerName != null) && this.providerName.equals(rhs.providerName)))) &&
                ((this.height == rhs.height) || ((this.height != null) && this.height.equals(rhs.height))));
    }

}
