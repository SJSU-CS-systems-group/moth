
// THIS FILE WAS GENERATED BY JSON2JAVA
// IT HAS NOT BEEN CHANGED. (IF IT HAS REMOVE THIS LINE)
// CHANGES MADE:
//   * NONE SO FAR


package edu.sjsu.moth.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "length",
    "duration",
    "fps",
    "size",
    "width",
    "height",
    "aspect",
    "audio_encode",
    "audio_bitrate",
    "audio_channels",
    "original",
    "small"
})
public class Meta {

    @JsonProperty("length")
    public String length;
    @JsonProperty("duration")
    public Double duration;
    @JsonProperty("fps")
    public Integer fps;
    @JsonProperty("size")
    public String size;
    @JsonProperty("width")
    public Integer width;
    @JsonProperty("height")
    public Integer height;
    @JsonProperty("aspect")
    public Double aspect;
    @JsonProperty("audio_encode")
    public String audioEncode;
    @JsonProperty("audio_bitrate")
    public String audioBitrate;
    @JsonProperty("audio_channels")
    public String audioChannels;
    @JsonProperty("original")
    public Original original;
    @JsonProperty("small")
    public Small small;

    /**
     * No args constructor for use in serialization
     * 
     */
    public Meta() {
    }

    public Meta(String length, Double duration, Integer fps, String size, Integer width, Integer height, Double aspect, String audioEncode, String audioBitrate, String audioChannels, Original original, Small small) {
        super();
        this.length = length;
        this.duration = duration;
        this.fps = fps;
        this.size = size;
        this.width = width;
        this.height = height;
        this.aspect = aspect;
        this.audioEncode = audioEncode;
        this.audioBitrate = audioBitrate;
        this.audioChannels = audioChannels;
        this.original = original;
        this.small = small;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Meta.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("length");
        sb.append('=');
        sb.append(((this.length == null)?"<null>":this.length));
        sb.append(',');
        sb.append("duration");
        sb.append('=');
        sb.append(((this.duration == null)?"<null>":this.duration));
        sb.append(',');
        sb.append("fps");
        sb.append('=');
        sb.append(((this.fps == null)?"<null>":this.fps));
        sb.append(',');
        sb.append("size");
        sb.append('=');
        sb.append(((this.size == null)?"<null>":this.size));
        sb.append(',');
        sb.append("width");
        sb.append('=');
        sb.append(((this.width == null)?"<null>":this.width));
        sb.append(',');
        sb.append("height");
        sb.append('=');
        sb.append(((this.height == null)?"<null>":this.height));
        sb.append(',');
        sb.append("aspect");
        sb.append('=');
        sb.append(((this.aspect == null)?"<null>":this.aspect));
        sb.append(',');
        sb.append("audioEncode");
        sb.append('=');
        sb.append(((this.audioEncode == null)?"<null>":this.audioEncode));
        sb.append(',');
        sb.append("audioBitrate");
        sb.append('=');
        sb.append(((this.audioBitrate == null)?"<null>":this.audioBitrate));
        sb.append(',');
        sb.append("audioChannels");
        sb.append('=');
        sb.append(((this.audioChannels == null)?"<null>":this.audioChannels));
        sb.append(',');
        sb.append("original");
        sb.append('=');
        sb.append(((this.original == null)?"<null>":this.original));
        sb.append(',');
        sb.append("small");
        sb.append('=');
        sb.append(((this.small == null)?"<null>":this.small));
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
        result = ((result* 31)+((this.small == null)? 0 :this.small.hashCode()));
        result = ((result* 31)+((this.original == null)? 0 :this.original.hashCode()));
        result = ((result* 31)+((this.length == null)? 0 :this.length.hashCode()));
        result = ((result* 31)+((this.fps == null)? 0 :this.fps.hashCode()));
        result = ((result* 31)+((this.audioBitrate == null)? 0 :this.audioBitrate.hashCode()));
        result = ((result* 31)+((this.duration == null)? 0 :this.duration.hashCode()));
        result = ((result* 31)+((this.audioEncode == null)? 0 :this.audioEncode.hashCode()));
        result = ((result* 31)+((this.audioChannels == null)? 0 :this.audioChannels.hashCode()));
        result = ((result* 31)+((this.size == null)? 0 :this.size.hashCode()));
        result = ((result* 31)+((this.aspect == null)? 0 :this.aspect.hashCode()));
        result = ((result* 31)+((this.width == null)? 0 :this.width.hashCode()));
        result = ((result* 31)+((this.height == null)? 0 :this.height.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Meta) == false) {
            return false;
        }
        Meta rhs = ((Meta) other);
        return (((((((((((((this.small == rhs.small)||((this.small!= null)&&this.small.equals(rhs.small)))&&((this.original == rhs.original)||((this.original!= null)&&this.original.equals(rhs.original))))&&((this.length == rhs.length)||((this.length!= null)&&this.length.equals(rhs.length))))&&((this.fps == rhs.fps)||((this.fps!= null)&&this.fps.equals(rhs.fps))))&&((this.audioBitrate == rhs.audioBitrate)||((this.audioBitrate!= null)&&this.audioBitrate.equals(rhs.audioBitrate))))&&((this.duration == rhs.duration)||((this.duration!= null)&&this.duration.equals(rhs.duration))))&&((this.audioEncode == rhs.audioEncode)||((this.audioEncode!= null)&&this.audioEncode.equals(rhs.audioEncode))))&&((this.audioChannels == rhs.audioChannels)||((this.audioChannels!= null)&&this.audioChannels.equals(rhs.audioChannels))))&&((this.size == rhs.size)||((this.size!= null)&&this.size.equals(rhs.size))))&&((this.aspect == rhs.aspect)||((this.aspect!= null)&&this.aspect.equals(rhs.aspect))))&&((this.width == rhs.width)||((this.width!= null)&&this.width.equals(rhs.width))))&&((this.height == rhs.height)||((this.height!= null)&&this.height.equals(rhs.height))));
    }

}