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
@JsonPropertyOrder({ "sharedInbox" })
public class Endpoints {

    @JsonProperty("sharedInbox")
    public String sharedInbox;

    /**
     * No args constructor for use in serialization
     */
    public Endpoints() {}

    public Endpoints(String sharedInbox) {
        super();
        this.sharedInbox = sharedInbox;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Endpoints.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this)))
                .append('[');
        sb.append("sharedInbox");
        sb.append('=');
        sb.append(((this.sharedInbox == null) ? "<null>" : this.sharedInbox));
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
        result = ((result * 31) + ((this.sharedInbox == null) ? 0 : this.sharedInbox.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof Endpoints)) {
            return false;
        }
        Endpoints rhs = ((Endpoints) other);
        return (Objects.equals(this.sharedInbox, rhs.sharedInbox));
    }

}