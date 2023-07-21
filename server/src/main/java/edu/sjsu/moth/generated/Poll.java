// THIS FILE WAS GENERATED BY JSON2JAVA
// CHANGES MADE:
//   * voters_count changed to Integer type from Object
//   * emojis changed to CustomEmoji type

package edu.sjsu.moth.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "id", "expires_at", "expired", "multiple", "votes_count", "voters_count", "voted", "own_votes",
        "options", "emojis" })
public class Poll {

    @JsonProperty("id")
    public String id;
    @JsonProperty("expires_at")
    public String expiresAt;
    @JsonProperty("expired")
    public Boolean expired;
    @JsonProperty("multiple")
    public Boolean multiple;
    @JsonProperty("votes_count")
    public Integer votesCount;
    @JsonProperty("voters_count")
    public Integer votersCount;
    @JsonProperty("voted")
    public Boolean voted;
    @JsonProperty("own_votes")
    public List<Integer> ownVotes = new ArrayList<Integer>();
    @JsonProperty("options")
    public List<Option> options = new ArrayList<Option>();
    @JsonProperty("emojis")
    public List<CustomEmoji> emojis = new ArrayList<CustomEmoji>();

    /**
     * No args constructor for use in serialization
     */
    public Poll() {
    }

    public Poll(String id, String expiresAt, Boolean expired, Boolean multiple, Integer votesCount,
                Integer votersCount, Boolean voted, List<Integer> ownVotes, List<Option> options,
                List<CustomEmoji> emojis) {
        super();
        this.id = id;
        this.expiresAt = expiresAt;
        this.expired = expired;
        this.multiple = multiple;
        this.votesCount = votesCount;
        this.votersCount = votersCount;
        this.voted = voted;
        this.ownVotes = ownVotes;
        this.options = options;
        this.emojis = emojis;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Poll.class.getName())
                .append('@')
                .append(Integer.toHexString(System.identityHashCode(this)))
                .append('[');
        sb.append("id");
        sb.append('=');
        sb.append(((this.id == null) ? "<null>" : this.id));
        sb.append(',');
        sb.append("expiresAt");
        sb.append('=');
        sb.append(((this.expiresAt == null) ? "<null>" : this.expiresAt));
        sb.append(',');
        sb.append("expired");
        sb.append('=');
        sb.append(((this.expired == null) ? "<null>" : this.expired));
        sb.append(',');
        sb.append("multiple");
        sb.append('=');
        sb.append(((this.multiple == null) ? "<null>" : this.multiple));
        sb.append(',');
        sb.append("votesCount");
        sb.append('=');
        sb.append(((this.votesCount == null) ? "<null>" : this.votesCount));
        sb.append(',');
        sb.append("votersCount");
        sb.append('=');
        sb.append(((this.votersCount == null) ? "<null>" : this.votersCount));
        sb.append(',');
        sb.append("voted");
        sb.append('=');
        sb.append(((this.voted == null) ? "<null>" : this.voted));
        sb.append(',');
        sb.append("ownVotes");
        sb.append('=');
        sb.append(((this.ownVotes == null) ? "<null>" : this.ownVotes));
        sb.append(',');
        sb.append("options");
        sb.append('=');
        sb.append(((this.options == null) ? "<null>" : this.options));
        sb.append(',');
        sb.append("emojis");
        sb.append('=');
        sb.append(((this.emojis == null) ? "<null>" : this.emojis));
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
        result = ((result * 31) + ((this.expired == null) ? 0 : this.expired.hashCode()));
        result = ((result * 31) + ((this.votesCount == null) ? 0 : this.votesCount.hashCode()));
        result = ((result * 31) + ((this.votersCount == null) ? 0 : this.votersCount.hashCode()));
        result = ((result * 31) + ((this.multiple == null) ? 0 : this.multiple.hashCode()));
        result = ((result * 31) + ((this.options == null) ? 0 : this.options.hashCode()));
        result = ((result * 31) + ((this.voted == null) ? 0 : this.voted.hashCode()));
        result = ((result * 31) + ((this.id == null) ? 0 : this.id.hashCode()));
        result = ((result * 31) + ((this.expiresAt == null) ? 0 : this.expiresAt.hashCode()));
        result = ((result * 31) + ((this.ownVotes == null) ? 0 : this.ownVotes.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof Poll rhs)) {
            return false;
        }
        return ((((((((((Objects.equals(this.emojis, rhs.emojis)) && (Objects.equals(this.expired,
                                                                                     rhs.expired))) && (Objects.equals(
                this.votesCount, rhs.votesCount))) && (Objects.equals(this.votersCount,
                                                                      rhs.votersCount))) && (Objects.equals(
                this.multiple, rhs.multiple))) && (Objects.equals(this.options, rhs.options))) && (Objects.equals(
                this.voted, rhs.voted))) && (Objects.equals(this.id, rhs.id))) && (Objects.equals(this.expiresAt,
                                                                                                  rhs.expiresAt))) && (Objects.equals(
                this.ownVotes, rhs.ownVotes)));
    }

}
