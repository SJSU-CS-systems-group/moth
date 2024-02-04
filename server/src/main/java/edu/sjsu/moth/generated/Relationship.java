
// THIS FILE WAS GENERATED BY JSON2JAVA
// IT HAS NOT BEEN CHANGED. (IF IT HAS REMOVE THIS LINE)
// CHANGES MADE:
//   * NONE SO FAR
// generated from https://docs.joinmastodon.org/entities/Relationship/

package edu.sjsu.moth.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "id",
    "following",
    "showing_reblogs",
    "notifying",
    "followed_by",
    "blocking",
    "blocked_by",
    "muting",
    "muting_notifications",
    "requested",
    "requested_by",
    "domain_blocking",
    "endorsed",
    "note"
})
public class Relationship {

    @JsonProperty("id")
    public String id;
    @JsonProperty("following")
    public Boolean following;
    @JsonProperty("showing_reblogs")
    public Boolean showingReblogs;
    @JsonProperty("notifying")
    public Boolean notifying;
    @JsonProperty("followed_by")
    public Boolean followedBy;
    @JsonProperty("blocking")
    public Boolean blocking;
    @JsonProperty("blocked_by")
    public Boolean blockedBy;
    @JsonProperty("muting")
    public Boolean muting;
    @JsonProperty("muting_notifications")
    public Boolean mutingNotifications;
    @JsonProperty("requested")
    public Boolean requested;
    @JsonProperty("requested_by")
    public Boolean requestedBy;
    @JsonProperty("domain_blocking")
    public Boolean domainBlocking;
    @JsonProperty("endorsed")
    public Boolean endorsed;
    @JsonProperty("note")
    public String note;

    /**
     * No args constructor for use in serialization
     * 
     */
    public Relationship() {
    }

    public Relationship(String id, Boolean following, Boolean showingReblogs, Boolean notifying, Boolean followedBy, Boolean blocking, Boolean blockedBy, Boolean muting, Boolean mutingNotifications, Boolean requested, Boolean requestedBy, Boolean domainBlocking, Boolean endorsed, String note) {
        super();
        this.id = id;
        this.following = following;
        this.showingReblogs = showingReblogs;
        this.notifying = notifying;
        this.followedBy = followedBy;
        this.blocking = blocking;
        this.blockedBy = blockedBy;
        this.muting = muting;
        this.mutingNotifications = mutingNotifications;
        this.requested = requested;
        this.requestedBy = requestedBy;
        this.domainBlocking = domainBlocking;
        this.endorsed = endorsed;
        this.note = note;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Relationship.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("id");
        sb.append('=');
        sb.append(((this.id == null)?"<null>":this.id));
        sb.append(',');
        sb.append("following");
        sb.append('=');
        sb.append(((this.following == null)?"<null>":this.following));
        sb.append(',');
        sb.append("showingReblogs");
        sb.append('=');
        sb.append(((this.showingReblogs == null)?"<null>":this.showingReblogs));
        sb.append(',');
        sb.append("notifying");
        sb.append('=');
        sb.append(((this.notifying == null)?"<null>":this.notifying));
        sb.append(',');
        sb.append("followedBy");
        sb.append('=');
        sb.append(((this.followedBy == null)?"<null>":this.followedBy));
        sb.append(',');
        sb.append("blocking");
        sb.append('=');
        sb.append(((this.blocking == null)?"<null>":this.blocking));
        sb.append(',');
        sb.append("blockedBy");
        sb.append('=');
        sb.append(((this.blockedBy == null)?"<null>":this.blockedBy));
        sb.append(',');
        sb.append("muting");
        sb.append('=');
        sb.append(((this.muting == null)?"<null>":this.muting));
        sb.append(',');
        sb.append("mutingNotifications");
        sb.append('=');
        sb.append(((this.mutingNotifications == null)?"<null>":this.mutingNotifications));
        sb.append(',');
        sb.append("requested");
        sb.append('=');
        sb.append(((this.requested == null)?"<null>":this.requested));
        sb.append(',');
        sb.append("requestedBy");
        sb.append('=');
        sb.append(((this.requestedBy == null)?"<null>":this.requestedBy));
        sb.append(',');
        sb.append("domainBlocking");
        sb.append('=');
        sb.append(((this.domainBlocking == null)?"<null>":this.domainBlocking));
        sb.append(',');
        sb.append("endorsed");
        sb.append('=');
        sb.append(((this.endorsed == null)?"<null>":this.endorsed));
        sb.append(',');
        sb.append("note");
        sb.append('=');
        sb.append(((this.note == null)?"<null>":this.note));
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
        result = ((result* 31)+((this.note == null)? 0 :this.note.hashCode()));
        result = ((result* 31)+((this.notifying == null)? 0 :this.notifying.hashCode()));
        result = ((result* 31)+((this.blockedBy == null)? 0 :this.blockedBy.hashCode()));
        result = ((result* 31)+((this.mutingNotifications == null)? 0 :this.mutingNotifications.hashCode()));
        result = ((result* 31)+((this.endorsed == null)? 0 :this.endorsed.hashCode()));
        result = ((result* 31)+((this.muting == null)? 0 :this.muting.hashCode()));
        result = ((result* 31)+((this.requestedBy == null)? 0 :this.requestedBy.hashCode()));
        result = ((result* 31)+((this.followedBy == null)? 0 :this.followedBy.hashCode()));
        result = ((result* 31)+((this.requested == null)? 0 :this.requested.hashCode()));
        result = ((result* 31)+((this.blocking == null)? 0 :this.blocking.hashCode()));
        result = ((result* 31)+((this.following == null)? 0 :this.following.hashCode()));
        result = ((result* 31)+((this.id == null)? 0 :this.id.hashCode()));
        result = ((result* 31)+((this.showingReblogs == null)? 0 :this.showingReblogs.hashCode()));
        result = ((result* 31)+((this.domainBlocking == null)? 0 :this.domainBlocking.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Relationship) == false) {
            return false;
        }
        Relationship rhs = ((Relationship) other);
        return (((((((((((((((this.note == rhs.note)||((this.note!= null)&&this.note.equals(rhs.note)))&&((this.notifying == rhs.notifying)||((this.notifying!= null)&&this.notifying.equals(rhs.notifying))))&&((this.blockedBy == rhs.blockedBy)||((this.blockedBy!= null)&&this.blockedBy.equals(rhs.blockedBy))))&&((this.mutingNotifications == rhs.mutingNotifications)||((this.mutingNotifications!= null)&&this.mutingNotifications.equals(rhs.mutingNotifications))))&&((this.endorsed == rhs.endorsed)||((this.endorsed!= null)&&this.endorsed.equals(rhs.endorsed))))&&((this.muting == rhs.muting)||((this.muting!= null)&&this.muting.equals(rhs.muting))))&&((this.requestedBy == rhs.requestedBy)||((this.requestedBy!= null)&&this.requestedBy.equals(rhs.requestedBy))))&&((this.followedBy == rhs.followedBy)||((this.followedBy!= null)&&this.followedBy.equals(rhs.followedBy))))&&((this.requested == rhs.requested)||((this.requested!= null)&&this.requested.equals(rhs.requested))))&&((this.blocking == rhs.blocking)||((this.blocking!= null)&&this.blocking.equals(rhs.blocking))))&&((this.following == rhs.following)||((this.following!= null)&&this.following.equals(rhs.following))))&&((this.id == rhs.id)||((this.id!= null)&&this.id.equals(rhs.id))))&&((this.showingReblogs == rhs.showingReblogs)||((this.showingReblogs!= null)&&this.showingReblogs.equals(rhs.showingReblogs))))&&((this.domainBlocking == rhs.domainBlocking)||((this.domainBlocking!= null)&&this.domainBlocking.equals(rhs.domainBlocking))));
    }

}
