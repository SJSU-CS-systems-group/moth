// THIS FILE WAS GENERATED BY JSON2JAVA
// IT HAS NOT BEEN CHANGED. (IF IT HAS REMOVE THIS LINE)
// CHANGES MADE:
//   * ADDED MORE FIELDS: PURPOSE, SIZES, SRC

package edu.sjsu.moth.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "type", "mediaType", "url", "src", "sizes", "purpose" })
public class Icon {

    @JsonProperty("type")
    public String type;
    @JsonProperty("mediaType")
    public String mediaType;
    @JsonProperty("url")
    public String url;
    @JsonProperty("src")
    public String src;
    @JsonProperty("sizes")
    public String sizes;
    @JsonProperty("purpose")
    public String purpose;

    public Icon() {
    }

    public Icon(String type, String mediaType, String url, String src, String sizes, String purpose) {
        super();
        this.type = type;
        this.mediaType = mediaType;
        this.url = url;
        this.src = src;
        this.sizes = sizes;
        this.purpose = purpose;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Icon.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this)))
                .append('[');
        sb.append("type");
        sb.append('=');
        sb.append(((this.type == null) ? "<null>" : this.type));
        sb.append(',');
        sb.append("mediaType");
        sb.append('=');
        sb.append(((this.mediaType == null) ? "<null>" : this.mediaType));
        sb.append(',');
        sb.append("url");
        sb.append('=');
        sb.append(((this.url == null) ? "<null>" : this.url));
        sb.append(',');
        sb.append("src");
        sb.append('=');
        sb.append(((this.src == null) ? "<null>" : this.src));
        sb.append(',');
        sb.append("sizes");
        sb.append('=');
        sb.append(((this.sizes == null) ? "<null>" : this.sizes));
        sb.append(',');
        sb.append("purpose");
        sb.append('=');
        sb.append(((this.purpose == null) ? "<null>" : this.purpose));
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
        result = ((result * 31) + ((this.mediaType == null) ? 0 : this.mediaType.hashCode()));
        result = ((result * 31) + ((this.type == null) ? 0 : this.type.hashCode()));
        result = ((result * 31) + ((this.url == null) ? 0 : this.url.hashCode()));
        result = ((result * 31) + ((this.src == null) ? 0 : this.src.hashCode()));
        result = ((result * 31) + ((this.sizes == null) ? 0 : this.sizes.hashCode()));
        result = ((result * 31) + ((this.purpose == null) ? 0 : this.purpose.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof Icon rhs)) {
            return false;
        }
        return (((Objects.equals(this.mediaType, rhs.mediaType)) && (Objects.equals(this.type, rhs.type))) &&
                (Objects.equals(this.url, rhs.url)) && (Objects.equals(this.src, rhs.src)) &&
                (Objects.equals(this.sizes, rhs.sizes)) && (Objects.equals(this.purpose, rhs.purpose)));
    }

}
